package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.authz.sdk.PermissionCache;
import com.flowbinary.platformservice.authz.service.EffectivePermissionService;
import com.flowbinary.platformservice.authz.service.Operators;
import com.flowbinary.platformservice.authz.service.TenantFeatures;
import com.flowbinary.platformservice.config.AppProperties;
import com.flowbinary.platformservice.config.JpaConfig;
import com.flowbinary.platformservice.dto.bootstrap.MenuEntry;
import com.flowbinary.platformservice.dto.menu.DraftOpsRequest;
import com.flowbinary.platformservice.dto.menu.PreviewRequest;
import com.flowbinary.platformservice.dto.menu.TemporaryMenuRequest;
import com.flowbinary.platformservice.dto.menu.TemporaryMenuRequest.Audience;
import com.flowbinary.platformservice.model.MenuOp;
import com.flowbinary.platformservice.model.MenuOpType;
import com.flowbinary.platformservice.security.TenantContextResolver;
import com.flowbinary.platformservice.service.MenuAdminAccess.AdminContext;
import com.flowbinary.platformservice.service.MenuAdminAccess.ScopeKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The version-keyed catalogue cache against a real schema: what is a hit, what is
 * a miss, how stale a menu can be, which writes move which counter, and that
 * previews and temporary-menu windows never depend on a rebuild.
 *
 * <p>Opt-in, same as {@link MenuAdminIT}: set MENU_IT_DB_URL to an EMPTY scratch database.
 * The version-check interval is 1.5 s here so staleness is observable and deterministic.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "MENU_IT_DB_URL", matches = ".+")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({JpaConfig.class, AppProperties.class, MenuScopeLoader.class, MenuChangeApplier.class,
        MenuHierarchyValidator.class, MenuReferenceLookup.class, MenuDraftService.class, MenuPublishService.class,
        MenuPreviewService.class, MenuCatalogueService.class, AuditService.class, MenuAdminAccess.class,
        MenuResolutionService.class, MenuQueryService.class, SettingsResolver.class, TenantContextResolver.class,
        EffectivePermissionService.class, TenantFeatures.class, PermissionCache.class, Operators.class, TemporaryMenuService.class, TemporaryMenuLifecycleService.class,
        MenuCatalogueCache.class, MenuCatalogueCompiler.class, MenuMetrics.class, MenuCatalogEvents.class})
class MenuCacheIT {

    static final long CHECK_MS = 1500;

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> System.getenv("MENU_IT_DB_URL"));
        r.add("spring.datasource.username", () -> Optional.ofNullable(System.getenv("MENU_IT_DB_USER")).orElse(System.getProperty("user.name")));
        r.add("spring.datasource.password", () -> Optional.ofNullable(System.getenv("MENU_IT_DB_PASSWORD")).orElse(""));
        r.add("spring.flyway.enabled", () -> "true");
        r.add("app.menu.cache.version-check", () -> CHECK_MS + "ms");
        r.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired MenuAdminAccess access;
    @Autowired MenuDraftService drafts;
    @Autowired MenuPublishService publish;
    @Autowired MenuPreviewService preview;
    @Autowired MenuQueryService menuQuery;
    @Autowired TemporaryMenuService temps;
    @Autowired MenuCatalogueCache cache;
    @Autowired MenuCatalogueCompiler compiler;
    @Autowired MenuMetrics metrics;
    @Autowired jakarta.persistence.EntityManagerFactory emf;

    private double hitBase, missBase;

    @BeforeEach
    void cold() {
        MenuItFixtures.ensureItems(jdbc);
        cache.clear();
        // the meter registry outlives a test; count from here
        hitBase = rawGets("hit");
        missBase = rawGets("miss");
    }

    // ── fixtures ───────────────────────────────────────────────────────────

    record Person(UUID authId, UUID userId, String orgId, UUID tenantId) {
    }

    private UUID tenant(String orgId) {
        UUID id = UUID.randomUUID();
        String slug = "c" + id.toString().substring(0, 8);
        jdbc.update("INSERT INTO tenants (id, tenant_id, name, display_name, email, slug) VALUES (?,?,?,?,?,?)",
                id, orgId, slug, slug, slug + "@t.test", slug);
        return id;
    }

    private Person person(String role, String orgId, UUID tenantId) {
        UUID auth = UUID.randomUUID(), uid = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, tenant_id, auth_user_id, email, username) VALUES (?,?,?,?,?)",
                uid, tenantId, auth, auth + "@t.test", "u" + uid.toString().substring(0, 8));
        AuthzTestSupport.addMember(jdbc, tenantId, auth, role);
        return new Person(auth, uid, orgId, tenantId);
    }

    private JwtAuthenticationToken auth(Person p) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(p.authId().toString())
                .claim("organization", Map.of("org", Map.of("id", p.orgId()))).build();
        return new JwtAuthenticationToken(jwt);
    }

    private AdminContext ctx(Person p, ScopeKind kind) {
        return access.resolve(auth(p), "PROVIDER_CONSOLE", kind, null);
    }

    private static String uniq(String p) {
        return p + UUID.randomUUID().toString().substring(0, 6);
    }

    private static MenuOp put(String code, String label) {
        return MenuOp.builder().op(MenuOpType.PUT_ITEM).code(code).label(label).route("/" + code).build();
    }

    private void publishItem(AdminContext c, MenuOp op) {
        drafts.append(c, new DraftOpsRequest(null, null, List.of(op)));
        publish.publish(c, null);
    }

    private List<MenuEntry> menu(Person p) {
        return menuQuery.menuFor(auth(p), "PROVIDER_CONSOLE");
    }

    private Optional<MenuEntry> find(List<MenuEntry> tree, String id) {
        for (MenuEntry e : tree) {
            if (e.getId().equals(id)) return Optional.of(e);
            if (e.getChildren() != null) {
                Optional<MenuEntry> r = find(e.getChildren(), id);
                if (r.isPresent()) return r;
            }
        }
        return Optional.empty();
    }

    private double gets(String result) {
        return rawGets(result) - ("hit".equals(result) ? hitBase : missBase);
    }

    private double rawGets(String result) {
        return metrics.registry().get("cache.gets").tag("cache", "menu.catalogue").tag("result", result)
                .functionCounter().count();
    }

    private long version(UUID tenantId) {
        return jdbc.queryForObject("""
                SELECT COALESCE(MAX(version), 0) FROM menu_catalog_version
                WHERE application_id = (SELECT id FROM applications WHERE code='PROVIDER_CONSOLE')
                  AND tenant_id IS NOT DISTINCT FROM ?""", Long.class, tenantId);
    }

    // ── tests ──────────────────────────────────────────────────────────────

    @Test
    void secondRequestIsAHitAndConcurrentFirstRequestsCompileOnce() throws Exception {
        String org = UUID.randomUUID().toString();
        UUID t = tenant(org);
        Person doc = person("doctor", org, t);

        menu(doc);
        assertThat(gets("miss")).isEqualTo(1);
        menu(doc);
        menu(doc);
        assertThat(gets("miss")).isEqualTo(1);
        assertThat(gets("hit")).isEqualTo(2);

        // 16 simultaneous cold requests: one compile, everyone else waits for it
        cache.clear();
        double missesBefore = gets("miss");
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            results.add(pool.submit(() -> {
                go.await();
                return menu(doc).size();
            }));
        }
        go.countDown();
        for (Future<Integer> f : results) f.get(30, TimeUnit.SECONDS);
        pool.shutdown();
        assertThat(gets("miss") - missesBefore).isEqualTo(1);
    }

    @Test
    void publisherSeesTheirChangeImmediatelyEvenWithALongVersionCheck() {
        String org = UUID.randomUUID().toString();
        UUID t = tenant(org);
        AdminContext c = ctx(person("clinic_admin", org, t), ScopeKind.TENANT);
        Person doc = person("doctor", org, t);
        String code = uniq("pub");

        publishItem(c, put(code, "One"));
        assertThat(find(menu(doc), code)).map(MenuEntry::getLabel).contains("One");

        publishItem(c, MenuOp.builder().op(MenuOpType.PATCH_ITEM).code(code).label("Two").build());
        assertThat(find(menu(doc), code)).map(MenuEntry::getLabel).contains("Two");   // no 1.5 s wait
        assertThat(metrics.registry().counter("menu.publish", "scope", "tenant").count()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void changeFromAnotherInstanceIsPickedUpWithinTheVersionCheckInterval() throws Exception {
        String org = UUID.randomUUID().toString();
        UUID t = tenant(org);
        AdminContext c = ctx(person("clinic_admin", org, t), ScopeKind.TENANT);
        Person doc = person("doctor", org, t);
        String code = uniq("remote");
        publishItem(c, put(code, "Before"));
        assertThat(find(menu(doc), code)).map(MenuEntry::getLabel).contains("Before");

        // another instance writes (here: raw SQL); this instance has not been told
        jdbc.update("UPDATE menu_items SET label='After' WHERE code=?", code);
        assertThat(find(menu(doc), code)).map(MenuEntry::getLabel).contains("Before");   // within the interval: stale

        Thread.sleep(CHECK_MS + 300);
        assertThat(find(menu(doc), code)).map(MenuEntry::getLabel).contains("After");    // bounded staleness

        // and a Kafka-style nudge makes it immediate
        jdbc.update("UPDATE menu_items SET label='Again' WHERE code=?", code);
        cache.invalidateVersions();
        assertThat(find(menu(doc), code)).map(MenuEntry::getLabel).contains("Again");
    }

    @Test
    void everyTableFeedingTheCatalogueMovesTheRightCounter() {
        String org = UUID.randomUUID().toString();
        UUID t = tenant(org);
        UUID other = tenant(UUID.randomUUID().toString());
        AdminContext c = ctx(person("clinic_admin", org, t), ScopeKind.TENANT);
        String own = uniq("own");
        publishItem(c, put(own, "Own"));

        long g0 = version(null), t0 = version(t), o0 = version(other);

        // tenant-owned item and its children -> that tenant only
        jdbc.update("UPDATE menu_items SET label='x' WHERE code=?", own);
        assertThat(version(t)).isGreaterThan(t0);
        assertThat(version(null)).isEqualTo(g0);
        long t1 = version(t);
        jdbc.update("INSERT INTO menu_item_translations (menu_item_id, locale, label) SELECT id,'hi','x' FROM menu_items WHERE code=?", own);
        assertThat(version(t)).isGreaterThan(t1);
        long t2 = version(t);
        jdbc.update("DELETE FROM menu_plan_access WHERE menu_item_id=(SELECT id FROM menu_items WHERE code=?) AND plan='free'", own);
        assertThat(version(t)).isGreaterThan(t2);

        // platform item -> the global counter, which keys every tenant's catalogue
        long g1 = version(null);
        jdbc.update("UPDATE menu_items SET label=label WHERE code='fx_dash'");
        assertThat(version(null)).isGreaterThan(g1);
        assertThat(version(other)).isEqualTo(o0);

        // a tenant's override -> that tenant
        long t3 = version(t);
        jdbc.update("INSERT INTO menu_tenant_overrides (tenant_id, menu_item_id, label) SELECT ?, id, 'Home' FROM menu_items WHERE code='fx_dash'", t);
        assertThat(version(t)).isGreaterThan(t3);
        assertThat(version(other)).isEqualTo(o0);
    }

    @Test
    void perUserWritesDoNotMoveAnyCounter() {
        String org = UUID.randomUUID().toString();
        UUID t = tenant(org);
        Person doc = person("doctor", org, t);
        long g = version(null), v = version(t);
        jdbc.update("INSERT INTO menu_user_access (menu_item_id, user_id, effect) SELECT id, ?, 'DENY' FROM menu_items WHERE code='fx_dash'", doc.userId());
        assertThat(version(t)).isEqualTo(v);
        assertThat(version(null)).isEqualTo(g);
    }

    @Test
    void tenantsNeverShareACatalogueAndAPlatformChangeReachesAll() {
        String org1 = UUID.randomUUID().toString(), org2 = UUID.randomUUID().toString();
        UUID t1 = tenant(org1), t2 = tenant(org2);
        AdminContext c1 = ctx(person("clinic_admin", org1, t1), ScopeKind.TENANT);
        Person u1 = person("doctor", org1, t1), u2 = person("doctor", org2, t2);
        String mine = uniq("mine");

        publishItem(c1, put(mine, "Mine"));
        assertThat(find(menu(u1), mine)).isPresent();
        assertThat(find(menu(u2), mine)).isEmpty();                 // t2's cached catalogue never held it

        Person operator = person("superadmin", org1, t1);
        AdminContext g = ctx(operator, ScopeKind.GLOBAL);
        String glob = uniq("glob");
        menu(u2);                                                   // prime t2
        publishItem(g, put(glob, "Platform"));
        assertThat(find(menu(u2), glob)).isPresent();               // a global publish invalidated t2's entry too
        assertThat(find(menu(u1), glob)).isPresent();
    }

    @Test
    void previewSeesTheDraftWithoutPoisoningTheCache() {
        String org = UUID.randomUUID().toString();
        UUID t = tenant(org);
        AdminContext c = ctx(person("clinic_admin", org, t), ScopeKind.TENANT);
        Person doc = person("doctor", org, t);
        String code = uniq("draftonly");

        menu(doc);                                                  // prime the cache
        long before = version(t);
        drafts.append(c, new DraftOpsRequest(null, null, List.of(put(code, "Draft"))));

        var shown = preview.preview(c, new PreviewRequest(doc.userId(), null, true, null));
        assertThat(find(shown.menu(), code)).isPresent();           // the preview sees the draft
        assertThat(find(menu(doc), code)).isEmpty();                // the live menu never does
        assertThat(version(t)).isEqualTo(before);                   // and the rolled-back preview left no trace
    }

    @Test
    void temporaryWindowsAreHonouredFromTheCacheWithoutARebuild() throws Exception {
        String org = UUID.randomUUID().toString();
        UUID t = tenant(org);
        AdminContext c = ctx(person("clinic_admin", org, t), ScopeKind.TENANT);
        Person doc = person("doctor", org, t);
        String code = uniq("window");
        Instant now = Instant.now();

        temps.create(c, new TemporaryMenuRequest(code, code, null, null, "/" + code, null, null, null, null, null, null,
                now.plusMillis(1500), now.plusMillis(3500), null, null, null,
                new Audience("ALL_ELIGIBLE", null)));

        assertThat(find(menu(doc), code)).isEmpty();                // scheduled: in the cached catalogue but not yet live
        double misses = gets("miss");

        Thread.sleep(1700);                                         // the start has passed; nothing else happened
        cache.invalidateVersions();
        assertThat(find(menu(doc), code)).isPresent();
        assertThat(gets("miss")).isEqualTo(misses);                 // same catalogue, no rebuild

        Thread.sleep(2000);                                         // the end has passed
        assertThat(find(menu(doc), code)).isEmpty();
        assertThat(gets("miss")).isEqualTo(misses);
    }

    private long statements(Runnable work) {
        org.hibernate.stat.Statistics stats = emf.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        long before = stats.getPrepareStatementCount();
        work.run();
        return stats.getPrepareStatementCount() - before;
    }

    @Test
    void aWarmRequestSkipsTheCatalogueQueriesButStillReadsPerUserData() {
        String org = UUID.randomUUID().toString();
        UUID t = tenant(org);
        Person doc = person("doctor", org, t);
        menu(doc);                                   // settle one-off lookups (tenant, roles)
        cache.clear();

        long cold = statements(() -> menu(doc));
        long warm = statements(() -> menu(doc));
        System.out.println("MENU-STATEMENTS cold=" + cold + " warm=" + warm);

        assertThat(warm).isLessThan(cold);
        // The warm path skips the catalogue load (items, plan access, translations, overrides, version
        // counters). The remainder is per-request tenant/user/plan/settings data. Permission sets are
        // cached separately (PermissionCache), so roles and privileges cost nothing here once warm.
        assertThat(warm).isLessThanOrEqualTo(cold - 4);
    }
}
