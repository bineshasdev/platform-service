package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.config.AppProperties;
import com.flowbinary.platformservice.config.JpaConfig;
import com.flowbinary.platformservice.dto.bootstrap.MenuEntry;
import com.flowbinary.platformservice.dto.menu.DraftOpsRequest;
import com.flowbinary.platformservice.dto.menu.DraftResponse;
import com.flowbinary.platformservice.dto.menu.GroupRequest;
import com.flowbinary.platformservice.dto.menu.PreviewRequest;
import com.flowbinary.platformservice.dto.menu.TemporaryMenuRequest;
import com.flowbinary.platformservice.dto.menu.TemporaryMenuRequest.Audience;
import com.flowbinary.platformservice.exception.MenuConflictException;
import com.flowbinary.platformservice.exception.MenuDraftException;
import com.flowbinary.platformservice.model.*;
import com.flowbinary.platformservice.repository.*;
import com.flowbinary.platformservice.security.TenantContextResolver;
import com.flowbinary.platformservice.service.MenuAdminAccess.AdminContext;
import com.flowbinary.platformservice.service.MenuAdminAccess.ScopeKind;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Drives the real menu administration stack against a real PostgreSQL schema
 * built by Flyway: draft → dry-run → preview → publish → snapshot → rollback,
 * tenant isolation, and audit.
 *
 * <p>Opt-in. Point it at an EMPTY scratch database it may migrate and fill:
 * <pre>MENU_IT_DB_URL=jdbc:postgresql://localhost:5432/menu_it_scratch  [MENU_IT_DB_USER=..  MENU_IT_DB_PASSWORD=..]</pre>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "MENU_IT_DB_URL", matches = ".+")
@Transactional(propagation = Propagation.NOT_SUPPORTED)   // the services manage their own transactions
@Import({JpaConfig.class, AppProperties.class, MenuScopeLoader.class, MenuChangeApplier.class,
        MenuHierarchyValidator.class, MenuReferenceLookup.class, MenuDraftService.class, MenuPublishService.class,
        MenuPreviewService.class, MenuCatalogueService.class, AuditService.class, MenuAdminAccess.class,
        MenuResolutionService.class, MenuQueryService.class, UserGroupService.class, TemporaryMenuService.class, TemporaryMenuLifecycleService.class,
        MenuCatalogueCache.class, MenuCatalogueCompiler.class, MenuMetrics.class, MenuCatalogEvents.class, SettingsResolver.class, TenantContextResolver.class})
class MenuAdminIT {

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> System.getenv("MENU_IT_DB_URL"));
        r.add("spring.datasource.username", () -> Optional.ofNullable(System.getenv("MENU_IT_DB_USER")).orElse(System.getProperty("user.name")));
        r.add("spring.datasource.password", () -> Optional.ofNullable(System.getenv("MENU_IT_DB_PASSWORD")).orElse(""));
        r.add("spring.flyway.enabled", () -> "true");
        r.add("app.menu.cache.version-check", () -> "1ms");   // functional tests want every read fresh; MenuCacheIT tests the cache
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired MenuAdminAccess access;
    @Autowired MenuDraftService drafts;
    @Autowired MenuPublishService publish;
    @Autowired MenuPreviewService preview;
    @Autowired MenuCatalogueService catalogue;
    @Autowired MenuQueryService menuQuery;
    @Autowired MenuReferences refs;
    @Autowired MenuResolutionService resolution;
    @Autowired TenantRepository tenants;
    @Autowired TenantUserRepository users;
    @Autowired MenuItemRepository items;
    @Autowired AuditLogRepository auditLogs;
    @Autowired UserGroupService groups;
    @Autowired TemporaryMenuService temps;
    @Autowired TemporaryMenuLifecycleService lifecycle;

    @org.junit.jupiter.api.BeforeEach
    void items() {
        MenuItFixtures.ensureItems(jdbc);
    }

    // ── fixtures ───────────────────────────────────────────────────────────

    record Person(UUID authId, UUID userId, String orgId, UUID tenantId) {
    }

    private Person person(String platformRole, String tenantSlug, String orgId, UUID tenantId) {
        UUID auth = UUID.randomUUID();
        UUID uid = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, tenant_id, auth_user_id, role, email, username) VALUES (?,?,?,?,?,?)",
                uid, tenantId, auth, platformRole, auth + "@t.test", "u" + uid.toString().substring(0, 8));
        return new Person(auth, uid, orgId, tenantId);
    }

    private UUID tenant(String slug, String orgId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO tenants (id, tenant_id, name, display_name, email, slug) VALUES (?,?,?,?,?,?)",
                id, orgId, slug, slug, slug + "@t.test", slug);
        return id;
    }

    private JwtAuthenticationToken auth(Person p) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(p.authId().toString())
                .claim("organization", Map.of("org", Map.of("id", p.orgId()))).build();
        return new JwtAuthenticationToken(jwt);
    }

    private static String uniq(String p) {
        return p + UUID.randomUUID().toString().substring(0, 6);
    }

    private static MenuOp put(String code, String parent, String label, String route, String type, Set<String> roles) {
        return MenuOp.builder().op(MenuOpType.PUT_ITEM).code(code).parentCode(parent).label(label).route(route)
                .type(type).roles(roles).build();
    }

    private AdminContext ctx(Person p, ScopeKind kind, UUID tenantId) {
        return access.resolve(auth(p), "PROVIDER_CONSOLE", kind, tenantId);
    }

    private DraftResponse draft(AdminContext c, MenuOp... ops) {
        return drafts.append(c, new DraftOpsRequest(null, null, List.of(ops)));
    }

    private List<String> codes(List<MenuEntry> tree) {
        List<String> out = new ArrayList<>();
        for (MenuEntry e : tree) {
            out.add(e.getId());
            if (e.getChildren() != null) out.addAll(codes(e.getChildren()));
        }
        return out;
    }

    // ── tests ──────────────────────────────────────────────────────────────

    @Test
    void catalogueRolesHoldTheNewPrivilegesTheCatalogueGivesThem() {
        Map<String, Set<String>> expected = Map.of(
                "OWNER",  Set.of("schedule.manage", "referral.view", "referral.manage", "telehealth.consult", "lab.sample.manage",
                                 "lab.catalog.manage", "pharmacy.inventory.manage", "data.import", "data.export"),
                "MANAGER", Set.of("schedule.manage"),
                "CLINIC_ADMIN", Set.of("schedule.manage", "referral.view", "data.import"),
                "DOCTOR", Set.of("referral.view", "referral.manage", "telehealth.consult"),
                "NURSE", Set.of("referral.view"),
                "LAB_ADMIN", Set.of("lab.sample.manage", "lab.catalog.manage"),
                "LAB_TECHNICIAN", Set.of("lab.sample.manage"),
                "PHLEBOTOMIST", Set.of("lab.sample.manage"),
                "PHARMACIST", Set.of("pharmacy.inventory.manage"));
        Set<String> all = expected.get("OWNER");
        expected.forEach((role, granted) -> {
            Set<String> held = new java.util.HashSet<>(jdbc.queryForList(
                    "SELECT p.code FROM role_privileges rp JOIN roles r ON r.id=rp.role_id AND r.tenant_id IS NULL "
                            + "JOIN privileges p ON p.id=rp.privilege_id WHERE r.code=? AND p.code = ANY(?)",
                    String.class, role, all.toArray(String[]::new)));
            assertThat(held).as(role).isEqualTo(granted);
        });
        // ADMIN matches OWNER; roles not listed hold none of them
        assertThat(jdbc.queryForObject("SELECT count(*) FROM role_privileges rp JOIN roles r ON r.id=rp.role_id AND r.code='ADMIN' "
                + "JOIN privileges p ON p.id=rp.privilege_id WHERE p.code = ANY(?)", Integer.class, (Object) all.toArray(String[]::new)))
                .isEqualTo(all.size());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM role_privileges rp JOIN roles r ON r.id=rp.role_id "
                + "JOIN privileges p ON p.id=rp.privilege_id WHERE r.code IN ('RECEPTIONIST','BILLING_STAFF','MEDICAL_CODER','COMPLIANCE_OFFICER','PATIENT','VIEWER','USER') "
                + "AND p.code = ANY(?)", Integer.class, (Object) all.toArray(String[]::new))).isZero();
    }

    @Test
    void tenantTypeGateAndPlacementFilter() {
        String clinicOrg = UUID.randomUUID().toString(), labOrg = UUID.randomUUID().toString();
        UUID clinic = tenant(uniq("c"), clinicOrg), lab = tenant(uniq("l"), labOrg);
        jdbc.update("UPDATE tenants SET type='CLINIC' WHERE id=?", clinic);
        jdbc.update("UPDATE tenants SET type='LAB' WHERE id=?", lab);
        Person operator = person("superadmin", "x", clinicOrg, clinic);
        Person clinicDoc = person("doctor", "x", clinicOrg, clinic);
        Person labDoc = person("doctor", "x", labOrg, lab);
        String hub = uniq("hub"), page = uniq("page"), labOnly = uniq("labonly");

        AdminContext g = ctx(operator, ScopeKind.GLOBAL, null);
        draft(g, put(hub, null, "Hub", "/" + hub, "LINK", Set.of("DOCTOR")),
                 put(page, hub, "Page", "/" + hub + "/page", "LINK", Set.of("DOCTOR")).toBuilder().placement("SETTINGS").build(),
                 put(labOnly, null, "Lab only", "/" + labOnly, "LINK", Set.of("DOCTOR")).toBuilder().tenantTypes(Set.of("LAB")).build());
        publish.publish(g, "8a");

        assertThat(codes(menuQuery.menuFor(auth(clinicDoc), "PROVIDER_CONSOLE"))).contains(hub, page).doesNotContain(labOnly);
        assertThat(codes(menuQuery.menuFor(auth(labDoc), "PROVIDER_CONSOLE"))).contains(hub, page, labOnly);

        // only the settings page, without the left-menu hub that holds it
        assertThat(menuQuery.menuFor(auth(clinicDoc), "PROVIDER_CONSOLE", MenuPlacement.SETTINGS))
                .extracting(MenuEntry::getId).contains(page).doesNotContain(hub);
        assertThat(codes(menuQuery.menuFor(auth(clinicDoc), "PROVIDER_CONSOLE", MenuPlacement.LEFT))).contains(hub, page);
        assertThat(jdbc.queryForObject("SELECT placement FROM menu_items WHERE code=?", String.class, page)).isEqualTo("SETTINGS");

        assertThat(catalogue.catalogue(g)).filteredOn(c -> c.code().equals(labOnly))
                .singleElement().satisfies(c -> assertThat(c.tenantTypes()).containsExactly("LAB"));

        // an empty set on PATCH clears the gate
        draft(g, MenuOp.builder().op(MenuOpType.PATCH_ITEM).code(labOnly).tenantTypes(Set.of()).build());
        publish.publish(g, "open to all");
        assertThat(jdbc.queryForObject("SELECT tenant_types IS NULL FROM menu_items WHERE code=?", Boolean.class, labOnly)).isTrue();
        assertThat(codes(menuQuery.menuFor(auth(clinicDoc), "PROVIDER_CONSOLE"))).contains(labOnly);

        // a tenant admin cannot gate their own items by tenant type
        Person clinicAdmin = person("clinic_admin", "x", clinicOrg, clinic);
        assertThatThrownBy(() -> draft(ctx(clinicAdmin, ScopeKind.TENANT, null),
                put(uniq("own"), null, "Own", "/own", "LINK", Set.of("DOCTOR")).toBuilder().tenantTypes(Set.of("LAB")).build()))
                .isInstanceOf(MenuDraftException.class).hasMessageContaining("global items");
    }

    @Test
    void operatorPublishesGlobalMenuAndTenantUserSeesItOnlyAfterPublish() {
        String org = UUID.randomUUID().toString();
        UUID t1 = tenant(uniq("g"), org);
        Person operator = person("superadmin", "x", org, t1);
        Person doctor = person("doctor", "x", org, t1);
        String grp = uniq("grp"), kid = uniq("kid");

        AdminContext g = ctx(operator, ScopeKind.GLOBAL, null);
        draft(g, put(grp, null, "Group", null, "GROUP", Set.of("DOCTOR")),
                 put(kid, grp, "Kid", "/kid", "LINK", Set.of("DOCTOR")));

        // draft only: nothing live yet
        assertThat(items.findByApplicationCodeAndCode("PROVIDER_CONSOLE", kid)).isEmpty();

        int before = publish.versions(g).stream().mapToInt(v -> v.version()).max().orElse(0);   // the global scope is shared
        var result = publish.publish(g, "first");
        assertThat(result.version()).isEqualTo(before + 1);
        assertThat(items.findByApplicationCodeAndCode("PROVIDER_CONSOLE", kid)).isPresent();
        assertThat(jdbc.queryForObject("SELECT depth FROM menu_items WHERE code=?", Integer.class, kid)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT path FROM menu_items WHERE code=?", String.class, kid)).isEqualTo(grp + "/" + kid);

        List<MenuEntry> menu = menuQuery.menuFor(auth(doctor), "PROVIDER_CONSOLE");
        assertThat(codes(menu)).contains(grp, kid);

        // audit: one row per change plus the publish row
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE entity_type='MENU_ITEM' AND entity_id IN (?,?)",
                Integer.class, grp, kid)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action='MENU_PUBLISHED'", Integer.class))
                .isGreaterThanOrEqualTo(1);
        // baseline + v1 snapshots
        assertThat(publish.versions(g)).extracting("version").contains(0, before + 1);
    }

    @Test
    void auditLogIsAppendOnly() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO audit_log (id, action, entity_type) VALUES (?, 'X', 'T')", id);
        assertThatThrownBy(() -> jdbc.update("UPDATE audit_log SET action='Y' WHERE id=?", id))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_log WHERE id=?", id))
                .hasMessageContaining("append-only");
    }

    @Test
    void tenantEditsStayInsideTheTenant() {
        String org1 = UUID.randomUUID().toString(), org2 = UUID.randomUUID().toString();
        UUID t1 = tenant(uniq("a"), org1), t2 = tenant(uniq("b"), org2);
        Person admin1 = person("clinic_admin", "x", org1, t1);
        Person staff1 = person("doctor", "x", org1, t1);
        Person staff2 = person("doctor", "x", org2, t2);
        String mine = uniq("mine");

        AdminContext c1 = ctx(admin1, ScopeKind.TENANT, null);
        draft(c1, put(mine, null, "Mine", "/mine", "LINK", Set.of("DOCTOR")));
        publish.publish(c1, null);

        assertThat(codes(menuQuery.menuFor(auth(staff1), "PROVIDER_CONSOLE"))).contains(mine);
        assertThat(codes(menuQuery.menuFor(auth(staff2), "PROVIDER_CONSOLE"))).doesNotContain(mine);
        assertThat(jdbc.queryForObject("SELECT tenant_id FROM menu_items WHERE code=?", UUID.class, mine)).isEqualTo(t1);

        // a tenant admin cannot reach the global scope or another tenant's scope
        assertThatThrownBy(() -> ctx(admin1, ScopeKind.GLOBAL, null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> ctx(admin1, ScopeKind.TENANT, t2)).isInstanceOf(AccessDeniedException.class);
        // ...and their catalogue never lists another tenant's items
        AdminContext c2 = ctx(person("clinic_admin", "x", org2, t2), ScopeKind.TENANT, null);
        assertThat(catalogue.catalogue(c2)).extracting("code").doesNotContain(mine);
    }

    @Test
    void tenantCannotEditGlobalItemsOrGrantSystemRolesOnThem() {
        String org = UUID.randomUUID().toString();
        UUID t = tenant(uniq("c"), org);
        Person admin = person("clinic_admin", "x", org, t);
        AdminContext c = ctx(admin, ScopeKind.TENANT, null);

        assertThatThrownBy(() -> draft(c, MenuOp.builder().op(MenuOpType.PATCH_ITEM).code("fx_dash").label("Hacked").build()))
                .isInstanceOf(MenuDraftException.class).hasMessageContaining("platform item");
        assertThatThrownBy(() -> draft(c, MenuOp.builder().op(MenuOpType.DELETE_ITEM).code("fx_dash").build()))
                .isInstanceOf(MenuDraftException.class);
        assertThatThrownBy(() -> draft(c, MenuOp.builder().op(MenuOpType.SET_ROLES).code("fx_dash").roles(Set.of("VIEWER")).build()))
                .isInstanceOf(MenuDraftException.class).hasMessageContaining("System roles");
        // unrelated rows untouched
        assertThat(jdbc.queryForObject("SELECT label FROM menu_items WHERE code='fx_dash'", String.class))
                .isNotEqualTo("Hacked");
    }

    @Test
    void tenantOverridesRelabelAndHideGlobalItemWithoutTouchingIt() {
        String org1 = UUID.randomUUID().toString(), org2 = UUID.randomUUID().toString();
        UUID t1 = tenant(uniq("d"), org1), t2 = tenant(uniq("e"), org2);
        Person admin = person("clinic_admin", "x", org1, t1);
        Person doc1 = person("clinic_admin", "x", org1, t1);
        Person doc2 = person("clinic_admin", "x", org2, t2);

        AdminContext c = ctx(admin, ScopeKind.TENANT, null);
        draft(c, MenuOp.builder().op(MenuOpType.SET_OVERRIDE).code("fx_dash").label("Home Base").build());
        publish.publish(c, null);

        assertThat(menuQuery.menuFor(auth(doc1), "PROVIDER_CONSOLE")).extracting(MenuEntry::getLabel).contains("Home Base");
        assertThat(menuQuery.menuFor(auth(doc2), "PROVIDER_CONSOLE")).extracting(MenuEntry::getLabel).doesNotContain("Home Base");
        assertThat(jdbc.queryForObject("SELECT label FROM menu_items WHERE code='fx_dash'", String.class)).isEqualTo("Fixture dashboard");
    }

    @Test
    void invalidDraftsAreRejectedAtEditTime() {
        String org = UUID.randomUUID().toString();
        UUID t = tenant(uniq("f"), org);
        AdminContext c = ctx(person("clinic_admin", "x", org, t), ScopeKind.TENANT, null);
        String a = uniq("a"), b = uniq("b"), cc = uniq("c"), d = uniq("d"), e = uniq("e");

        // bad route / injection
        assertThatThrownBy(() -> draft(c, put(uniq("x"), null, "X", "javascript:alert(1)", "LINK", Set.of())))
                .isInstanceOf(MenuDraftException.class).hasMessageContaining("route");
        assertThatThrownBy(() -> draft(c, put(uniq("x"), null, "X", "//evil.example", "LINK", Set.of())))
                .isInstanceOf(MenuDraftException.class);
        // unknown parent
        assertThatThrownBy(() -> draft(c, put(uniq("x"), "nope-" + a, "X", "/x", "LINK", Set.of())))
                .isInstanceOf(MenuDraftException.class);
        // 5 levels deep
        assertThatThrownBy(() -> draft(c, put(a, null, "A", "/a", "LINK", Set.of()),
                put(b, a, "B", "/b", "LINK", Set.of()), put(cc, b, "C", "/c", "LINK", Set.of()),
                put(d, cc, "D", "/d", "LINK", Set.of()), put(e, d, "E", "/e", "LINK", Set.of())))
                .isInstanceOf(MenuDraftException.class).hasMessageContaining("levels deep");
        // a code that equals a global one
        assertThatThrownBy(() -> draft(c, put("fx_dash", null, "Dup", "/dup", "LINK", Set.of())))
                .isInstanceOf(MenuDraftException.class);
        // nothing was stored
        assertThat(drafts.current(c)).isEmpty();
    }

    @Test
    void cycleIsRejected() {
        String org = UUID.randomUUID().toString();
        UUID t = tenant(uniq("h"), org);
        AdminContext c = ctx(person("clinic_admin", "x", org, t), ScopeKind.TENANT, null);
        String a = uniq("a"), b = uniq("b");
        assertThatThrownBy(() -> draft(c,
                put(a, null, "A", "/a", "LINK", Set.of()), put(b, a, "B", "/b", "LINK", Set.of()),
                MenuOp.builder().op(MenuOpType.MOVE_ITEM).code(a).parentCode(b).build()))
                .isInstanceOf(MenuDraftException.class).hasMessageContaining("cycle");
    }

    @Test
    void staleDraftCannotBePublishedUntilRebased() {
        String org = UUID.randomUUID().toString();
        UUID t = tenant(uniq("i"), org);
        Person admin = person("clinic_admin", "x", org, t);
        AdminContext c = ctx(admin, ScopeKind.TENANT, null);
        DraftResponse d = draft(c, put(uniq("p"), null, "P", "/p", "LINK", Set.of()));

        // simulate an out-of-band publish by bumping the scope version
        jdbc.update("INSERT INTO menu_snapshots (application_id, tenant_id, version, state) " +
                "SELECT id, ?, 1, '{}'::jsonb FROM applications WHERE code='PROVIDER_CONSOLE'", t);

        assertThatThrownBy(() -> publish.publish(c, null)).isInstanceOf(MenuConflictException.class);
        assertThat(drafts.current(c).orElseThrow().stale()).isTrue();
        drafts.rebase(c);
        assertThat(publish.publish(c, null).version()).isEqualTo(2);

        // appending without the current rowVersion is refused
        // the draft was published, so this starts a fresh one...
        drafts.append(c, new DraftOpsRequest(null, null, List.of(put(uniq("q"), null, "Q", "/q", "LINK", Set.of()))));
        // ...and a second append that does not carry its rowVersion is refused
        assertThatThrownBy(() -> drafts.append(c, new DraftOpsRequest(null, null, List.of(put(uniq("r"), null, "R", "/r", "LINK", Set.of())))))
                .isInstanceOf(MenuConflictException.class);
        assertThat(d.id()).isNotNull();
    }

    @Test
    void previewShowsDraftWithoutChangingAnything() {
        String org = UUID.randomUUID().toString();
        UUID t = tenant(uniq("j"), org);
        Person admin = person("clinic_admin", "x", org, t);
        Person doc = person("doctor", "x", org, t);
        AdminContext c = ctx(admin, ScopeKind.TENANT, null);
        String code = uniq("pv");
        draft(c, put(code, null, "Preview me", "/pv", "LINK", Set.of("DOCTOR")));

        var withDraft = preview.preview(c, new PreviewRequest(doc.userId(), null, true, null));
        var without = preview.preview(c, new PreviewRequest(doc.userId(), null, false, null));
        var asRole = preview.preview(c, new PreviewRequest(null, "DOCTOR", true, null));

        assertThat(withDraft.usedDraft()).isTrue();
        assertThat(codes(withDraft.menu())).contains(code);
        assertThat(codes(without.menu())).doesNotContain(code);
        assertThat(codes(asRole.menu())).contains(code);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM menu_items WHERE code=?", Integer.class, code)).isZero();

        // IDOR: a user of another tenant is indistinguishable from an unknown one
        String org2 = UUID.randomUUID().toString();
        UUID t2 = tenant(uniq("k"), org2);
        Person other = person("doctor", "x", org2, t2);
        assertThatThrownBy(() -> preview.preview(c, new PreviewRequest(other.userId(), null, true, null)))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void rollbackRestoresAnEarlierVersionThroughANewDraft() {
        String org = UUID.randomUUID().toString();
        UUID t = tenant(uniq("l"), org);
        Person admin = person("clinic_admin", "x", org, t);
        AdminContext c = ctx(admin, ScopeKind.TENANT, null);
        String keep = uniq("keep"), added = uniq("add");

        draft(c, put(keep, null, "Keep", "/keep", "LINK", Set.of("DOCTOR")));
        publish.publish(c, "v1");                                           // v1
        draft(c, put(added, null, "Added", "/added", "LINK", Set.of("DOCTOR")),
                MenuOp.builder().op(MenuOpType.PATCH_ITEM).code(keep).label("Renamed").build());
        publish.publish(c, "v2");                                           // v2
        assertThat(jdbc.queryForObject("SELECT label FROM menu_items WHERE code=?", String.class, keep)).isEqualTo("Renamed");

        DraftResponse rb = publish.rollback(c, 1);
        assertThat(rb.title()).isEqualTo("Rollback to v1");
        // nothing changed yet
        assertThat(jdbc.queryForObject("SELECT label FROM menu_items WHERE code=?", String.class, keep)).isEqualTo("Renamed");

        publish.publish(c, "back to v1");                                   // v3
        assertThat(jdbc.queryForObject("SELECT label FROM menu_items WHERE code=? ", String.class, keep)).isEqualTo("Keep");
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM menu_items WHERE code=?", Boolean.class, added)).isTrue();
        assertThat(publish.versions(c)).extracting("version").contains(3);
    }

    @Test
    void userAccessOverridesWorkAndDenyWins() {
        String org = UUID.randomUUID().toString();
        UUID t = tenant(uniq("m"), org);
        Person admin = person("clinic_admin", "x", org, t);
        Person doc = person("doctor", "x", org, t);
        AdminContext c = ctx(admin, ScopeKind.TENANT, null);

        // DENY fx_dash for the doctor
        draft(c, MenuOp.builder().op(MenuOpType.SET_USER_ACCESS).code("fx_dash").userId(doc.userId())
                .effect("DENY").reason("test").build());
        publish.publish(c, null);
        assertThat(codes(menuQuery.menuFor(auth(doc), "PROVIDER_CONSOLE"))).doesNotContain("fx_dash");

        // a user outside the tenant is rejected
        Person foreign = person("doctor", "x", UUID.randomUUID().toString(), tenant(uniq("n"), UUID.randomUUID().toString()));
        assertThatThrownBy(() -> draft(c, MenuOp.builder().op(MenuOpType.SET_USER_ACCESS).code("fx_dash")
                .userId(foreign.userId()).effect("GRANT").build()))
                .isInstanceOf(MenuDraftException.class).hasMessageContaining("not found");
    }

    @Test
    void menuWithoutDraftPrivilegeIsForbidden() {
        String org = UUID.randomUUID().toString();
        UUID t = tenant(uniq("o"), org);
        AdminContext viewer = ctx(person("guest", "x", org, t), ScopeKind.TENANT, null);
        assertThatThrownBy(() -> drafts.current(viewer)).isInstanceOf(AccessDeniedException.class);
    }

    // ── groups ─────────────────────────────────────────────────────────────

    private void group(AdminContext c, String code, UUID... members) {
        groups.create(c, new GroupRequest(code, code, null, null));
        if (members.length > 0) groups.addMembers(c, code, new HashSet<>(Arrays.asList(members)));
    }

    private static MenuOp groupOp(String item, String group, String effect) {
        return MenuOp.builder().op(MenuOpType.SET_GROUP_ACCESS).code(item).group(group).effect(effect).build();
    }

    @Test
    void groupGrantShowsItemAndAnyDenyBeatsAUserGrant() {
        String org = UUID.randomUUID().toString();
        UUID t = tenant(uniq("g1"), org);
        Person admin = person("clinic_admin", "x", org, t);
        Person doc = person("doctor", "x", org, t);
        AdminContext c = ctx(admin, ScopeKind.TENANT, null);
        String item = uniq("secret"), allow = uniq("allow"), block = uniq("block");

        // an item nobody's role grants
        draft(c, put(item, null, "Secret", "/secret", "LINK", Set.of()));
        publish.publish(c, null);
        assertThat(codes(menuQuery.menuFor(auth(doc), "PROVIDER_CONSOLE"))).doesNotContain(item);

        group(c, allow, doc.userId());
        draft(c, groupOp(item, allow, "GRANT"));
        publish.publish(c, null);
        assertThat(codes(menuQuery.menuFor(auth(doc), "PROVIDER_CONSOLE"))).contains(item);

        // a second group denies it, and the user is also granted it directly: deny still wins
        group(c, block, doc.userId());
        draft(c, groupOp(item, block, "DENY"),
                MenuOp.builder().op(MenuOpType.SET_USER_ACCESS).code(item).userId(doc.userId()).effect("GRANT").build());
        publish.publish(c, null);
        assertThat(codes(menuQuery.menuFor(auth(doc), "PROVIDER_CONSOLE"))).doesNotContain(item);

        // removing the member lifts the deny
        groups.removeMember(c, block, doc.userId());
        assertThat(codes(menuQuery.menuFor(auth(doc), "PROVIDER_CONSOLE"))).contains(item);
    }

    @Test
    void groupGrantCannotBypassPrivilegeGate() {
        String org = UUID.randomUUID().toString();
        UUID t = tenant(uniq("g2"), org);
        AdminContext c = ctx(person("clinic_admin", "x", org, t), ScopeKind.TENANT, null);
        Person doc = person("doctor", "x", org, t);
        String g = uniq("g");

        group(c, g, doc.userId());
        draft(c, groupOp("fx_admin_users", g, "GRANT"));       // fx_admin_users needs user.manage, a doctor lacks it
        publish.publish(c, null);
        assertThat(codes(menuQuery.menuFor(auth(doc), "PROVIDER_CONSOLE"))).doesNotContain("fx_admin_users");
    }

    @Test
    void groupsAreTenantOwned() {
        String org1 = UUID.randomUUID().toString(), org2 = UUID.randomUUID().toString();
        UUID t1 = tenant(uniq("g3"), org1), t2 = tenant(uniq("g4"), org2);
        AdminContext c1 = ctx(person("clinic_admin", "x", org1, t1), ScopeKind.TENANT, null);
        AdminContext c2 = ctx(person("clinic_admin", "x", org2, t2), ScopeKind.TENANT, null);
        Person u2 = person("doctor", "x", org2, t2);
        String shared = uniq("recep"), only2 = uniq("only2");

        group(c1, shared);
        group(c2, shared);                                   // same code in another tenant is fine
        group(c2, only2);

        assertThat(groups.list(c1)).extracting("code").contains(shared).doesNotContain(only2);
        // tenant 1 cannot target tenant 2's group in a draft, nor add tenant 2's user
        assertThatThrownBy(() -> draft(c1, groupOp("fx_dash", only2, "GRANT")))
                .isInstanceOf(MenuDraftException.class).hasMessageContaining("not found");
        assertThatThrownBy(() -> groups.addMembers(c1, shared, Set.of(u2.userId())))
                .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> groups.members(c1, only2)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> groups.create(c1, new GroupRequest(shared, "dup", null, null)))
                .isInstanceOf(MenuConflictException.class);
    }

    @Test
    void groupInUseCannotBeDeletedAndGlobalScopeRejectsGroupOps() {
        String org = UUID.randomUUID().toString();
        UUID t = tenant(uniq("g5"), org);
        AdminContext c = ctx(person("clinic_admin", "x", org, t), ScopeKind.TENANT, null);
        String g = uniq("g");
        group(c, g);
        draft(c, groupOp("fx_dash", g, "GRANT"));
        publish.publish(c, null);

        assertThatThrownBy(() -> groups.delete(c, g)).isInstanceOf(MenuConflictException.class);
        draft(c, groupOp("fx_dash", g, null));            // clear it, publish, then delete works
        publish.publish(c, null);
        groups.delete(c, g);
        assertThat(groups.list(c)).extracting("code").doesNotContain(g);

        Person operator = person("superadmin", "x", org, t);
        AdminContext global = ctx(operator, ScopeKind.GLOBAL, null);
        assertThatThrownBy(() -> draft(global, groupOp("fx_dash", "any", "GRANT")))
                .isInstanceOf(MenuDraftException.class).hasMessageContaining("tenant");
        assertThatThrownBy(() -> groups.list(global)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void rollbackRestoresGroupAssignmentsAndMembershipChangesAreAudited() {
        String org = UUID.randomUUID().toString();
        UUID t = tenant(uniq("g6"), org);
        AdminContext c = ctx(person("clinic_admin", "x", org, t), ScopeKind.TENANT, null);
        Person doc = person("doctor", "x", org, t);
        String g = uniq("g"), item = uniq("it");

        draft(c, put(item, null, "It", "/it", "LINK", Set.of()));
        publish.publish(c, "v1");
        group(c, g, doc.userId());
        draft(c, groupOp(item, g, "GRANT"));
        publish.publish(c, "v2");
        assertThat(codes(menuQuery.menuFor(auth(doc), "PROVIDER_CONSOLE"))).contains(item);

        publish.rollback(c, 1);
        publish.publish(c, "back");
        assertThat(codes(menuQuery.menuFor(auth(doc), "PROVIDER_CONSOLE"))).doesNotContain(item);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE entity_type='USER_GROUP_MEMBER' AND tenant_id=?",
                Integer.class, t)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE entity_type='MENU_GROUP_ACCESS' AND tenant_id=?",
                Integer.class, t)).isEqualTo(2);   // created in v2, removed by the rollback
    }

    // ── temporary menus ────────────────────────────────────────────────────

    private static TemporaryMenuRequest tmp(String code, String parent, Instant starts, Instant ends, Audience a) {
        return new TemporaryMenuRequest(code, code, "desc", "Sparkles", "/" + code, null, null, parent, null, null, "New",
                starts, ends, null, null, null, a);
    }

    private static Audience roles(String... r) {
        return new Audience("RESTRICTED", new HashSet<>(Arrays.asList(r)), null, null);
    }

    /** Moves a menu's window, standing in for the passage of time. */
    private void shift(String code, Instant starts, Instant ends) {
        jdbc.update("UPDATE menu_items SET starts_at=?, ends_at=? WHERE code=?",
                java.sql.Timestamp.from(starts), java.sql.Timestamp.from(ends), code);
    }

    private boolean sees(Person p, String code) {
        return codes(menuQuery.menuFor(auth(p), "PROVIDER_CONSOLE")).contains(code);
    }

    private String status(String code) {
        return jdbc.queryForObject("SELECT lifecycle_status FROM menu_items WHERE code=?", String.class, code);
    }

    private int auditCount(String action, String code) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action=? AND entity_id=?", Integer.class, action, code);
    }

    @Test
    void temporaryMenuShowsOnlyInsideItsWindowEvenIfTheSchedulerNeverRuns() {
        String org = UUID.randomUUID().toString();
        UUID t = tenant(uniq("tm1"), org);
        AdminContext c = ctx(person("clinic_admin", "x", org, t), ScopeKind.TENANT, null);
        Person doc = person("doctor", "x", org, t);
        String code = uniq("promo");
        Instant now = Instant.now();

        var created = temps.create(c, tmp(code, null, now.plus(Duration.ofHours(1)), now.plus(Duration.ofHours(2)), roles("DOCTOR")));
        assertThat(created.status()).isEqualTo("SCHEDULED");
        assertThat(created.live()).isFalse();
        assertThat(sees(doc, code)).isFalse();                       // before the start

        shift(code, now.minus(Duration.ofMinutes(1)), now.plus(Duration.ofHours(1)));
        assertThat(status(code)).isEqualTo("SCHEDULED");             // scheduler has not run...
        assertThat(sees(doc, code)).isTrue();                        // ...and it is shown anyway

        shift(code, now.minus(Duration.ofHours(2)), now.minus(Duration.ofSeconds(1)));
        assertThat(status(code)).isEqualTo("SCHEDULED");             // still stale
        assertThat(sees(doc, code)).isFalse();                       // yet gone once the end passes
    }

    @Test
    void schedulerKeepsStatusTruthfulAuditsOnceAndKeepsExpiredRows() throws Exception {
        String org = UUID.randomUUID().toString();
        UUID t = tenant(uniq("tm2"), org);
        AdminContext c = ctx(person("clinic_admin", "x", org, t), ScopeKind.TENANT, null);
        String a = uniq("act"), e = uniq("exp");
        Instant now = Instant.now();

        temps.create(c, tmp(a, null, now.plus(Duration.ofHours(1)), now.plus(Duration.ofHours(3)), roles("DOCTOR")));
        shift(a, now.minus(Duration.ofMinutes(5)), now.plus(Duration.ofHours(1)));
        lifecycle.transitionDue(Instant.now());
        assertThat(status(a)).isEqualTo("ACTIVE");
        assertThat(auditCount("MENU_TEMP_ACTIVATED", a)).isEqualTo(1);

        // a window that opened and closed unseen goes straight to EXPIRED, and two instances racing audit it once
        temps.create(c, tmp(e, null, now.plus(Duration.ofHours(1)), now.plus(Duration.ofHours(2)), roles("DOCTOR")));
        shift(e, now.minus(Duration.ofHours(2)), now.minus(Duration.ofMinutes(1)));
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Future<Integer>> runs = new ArrayList<>();
        for (int i = 0; i < 4; i++) runs.add(pool.submit(() -> lifecycle.transitionDue(Instant.now())));
        for (Future<Integer> f : runs) f.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(status(e)).isEqualTo("EXPIRED");
        assertThat(auditCount("MENU_TEMP_EXPIRED", e)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM menu_items WHERE code=?", Integer.class, e)).isEqualTo(1);   // kept

        // and the audit row names the scheduler, not a user
        assertThat(jdbc.queryForObject("SELECT actor_type || ':' || source FROM audit_log WHERE action='MENU_TEMP_EXPIRED' AND entity_id=?",
                String.class, e)).isEqualTo("SYSTEM:SCHEDULER");
    }

    @Test
    void expiredMenuCanBeRevivedAndCancelledOneCannot() {
        String org = UUID.randomUUID().toString();
        UUID t = tenant(uniq("tm3"), org);
        AdminContext c = ctx(person("clinic_admin", "x", org, t), ScopeKind.TENANT, null);
        Person doc = person("doctor", "x", org, t);
        String code = uniq("rev");
        Instant now = Instant.now();

        temps.create(c, tmp(code, null, null, now.plus(Duration.ofHours(1)), roles("DOCTOR")));
        assertThat(sees(doc, code)).isTrue();

        shift(code, now.minus(Duration.ofHours(2)), now.minus(Duration.ofHours(1)));
        lifecycle.transitionDue(Instant.now());
        assertThat(status(code)).isEqualTo("EXPIRED");
        assertThat(temps.list(c, MenuLifecycle.EXPIRED)).extracting("code").contains(code);

        var revived = temps.update(c, code, tmp(code, null, null, Instant.now().plus(Duration.ofDays(1)), roles("DOCTOR")));
        assertThat(revived.status()).isEqualTo("ACTIVE");
        assertThat(sees(doc, code)).isTrue();

        assertThat(temps.cancel(c, code, "campaign pulled").status()).isEqualTo("CANCELLED");
        assertThat(sees(doc, code)).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM menu_items WHERE code=?", Integer.class, code)).isEqualTo(1);
        assertThatThrownBy(() -> temps.update(c, code, tmp(code, null, null, Instant.now().plus(Duration.ofDays(1)), roles("DOCTOR"))))
                .isInstanceOf(MenuConflictException.class);
        assertThat(jdbc.queryForObject("SELECT reason FROM audit_log WHERE action='MENU_TEMP_CANCELLED' AND entity_id=?",
                String.class, code)).isEqualTo("campaign pulled");
    }

    @Test
    void invalidWindowsAndContentAreRejected() {
        String org = UUID.randomUUID().toString();
        UUID t = tenant(uniq("tm4"), org);
        AdminContext c = ctx(person("clinic_admin", "x", org, t), ScopeKind.TENANT, null);
        Instant now = Instant.now();

        assertThatThrownBy(() -> temps.create(c, tmp(uniq("a"), null, now.plusSeconds(100), now.plusSeconds(50), null)))
                .isInstanceOf(MenuDraftException.class).hasMessageContaining("after startsAt");
        assertThatThrownBy(() -> temps.create(c, tmp(uniq("a"), null, null, now.minusSeconds(1), null)))
                .isInstanceOf(MenuDraftException.class).hasMessageContaining("future");
        assertThatThrownBy(() -> temps.create(c, new TemporaryMenuRequest(uniq("a"), "x", null, null, "javascript:alert(1)",
                null, null, null, null, null, null, null, now.plusSeconds(60), null, null, null, null)))
                .isInstanceOf(MenuDraftException.class).hasMessageContaining("route");
        assertThatThrownBy(() -> temps.create(c, tmp("fx_dash", null, null, now.plusSeconds(60), null)))
                .isInstanceOf(MenuConflictException.class);
    }

    @Test
    void audienceRestrictsAndAllEligibleStillRespectsGates() {
        String org = UUID.randomUUID().toString();
        UUID t = tenant(uniq("tm5"), org);
        AdminContext c = ctx(person("clinic_admin", "x", org, t), ScopeKind.TENANT, null);
        Person doc = person("doctor", "x", org, t), other = person("doctor", "x", org, t), viewer = person("guest", "x", org, t);
        String byRole = uniq("r"), byUser = uniq("u"), byGroup = uniq("g"), everyone = uniq("all"), gated = uniq("gate"), grp = uniq("grp");
        Instant end = Instant.now().plus(Duration.ofHours(1));

        temps.create(c, tmp(byRole, null, null, end, roles("DOCTOR")));
        temps.create(c, tmp(byUser, null, null, end, new Audience("RESTRICTED", null, Set.of(doc.userId()), null)));
        group(c, grp, doc.userId());
        temps.create(c, tmp(byGroup, null, null, end, new Audience("RESTRICTED", null, null, Set.of(grp))));
        temps.create(c, tmp(everyone, null, null, end, new Audience("ALL_ELIGIBLE", null, null, null)));
        temps.create(c, new TemporaryMenuRequest(gated, gated, null, null, "/" + gated, null, null, null, null, null, null,
                null, end, "user.manage", null, null, new Audience("ALL_ELIGIBLE", null, null, null)));

        assertThat(codes(menuQuery.menuFor(auth(doc), "PROVIDER_CONSOLE"))).contains(byRole, byUser, byGroup, everyone).doesNotContain(gated);
        assertThat(codes(menuQuery.menuFor(auth(other), "PROVIDER_CONSOLE"))).contains(byRole, everyone)
                .doesNotContain(byUser, byGroup);
        assertThat(codes(menuQuery.menuFor(auth(viewer), "PROVIDER_CONSOLE"))).contains(everyone)
                .doesNotContain(byRole, byUser, byGroup, gated);

        // a user from another tenant cannot be targeted
        Person foreign = person("doctor", "x", UUID.randomUUID().toString(), tenant(uniq("tm5b"), UUID.randomUUID().toString()));
        assertThatThrownBy(() -> temps.create(c, tmp(uniq("f"), null, null, end,
                new Audience("RESTRICTED", null, Set.of(foreign.userId()), null))))
                .isInstanceOf(MenuDraftException.class).hasMessageContaining("not found");
    }

    @Test
    void operatorMenuReachesEveryTenantAndTenantMenuStaysInItsTenant() {
        String org1 = UUID.randomUUID().toString(), org2 = UUID.randomUUID().toString();
        UUID t1 = tenant(uniq("tm6"), org1), t2 = tenant(uniq("tm7"), org2);
        Person operator = person("superadmin", "x", org1, t1);
        AdminContext g = ctx(operator, ScopeKind.GLOBAL, null);
        AdminContext c1 = ctx(person("clinic_admin", "x", org1, t1), ScopeKind.TENANT, null);
        AdminContext c2 = ctx(person("clinic_admin", "x", org2, t2), ScopeKind.TENANT, null);
        Person u1 = person("doctor", "x", org1, t1), u2 = person("doctor", "x", org2, t2);
        String global = uniq("glob"), mine = uniq("mine");
        Instant end = Instant.now().plus(Duration.ofHours(1));

        temps.create(g, tmp(global, null, null, end, new Audience("ALL_ELIGIBLE", null, null, null)));
        temps.create(c1, tmp(mine, null, null, end, new Audience("ALL_ELIGIBLE", null, null, null)));

        assertThat(sees(u1, global)).isTrue();
        assertThat(sees(u2, global)).isTrue();
        assertThat(sees(u1, mine)).isTrue();
        assertThat(sees(u2, mine)).isFalse();

        // a tenant admin can neither read nor cancel a platform-wide menu, nor another tenant's
        assertThatThrownBy(() -> temps.get(c1, global)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> temps.cancel(c1, global, null)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> temps.cancel(c2, mine, null)).isInstanceOf(NoSuchElementException.class);
        assertThat(temps.list(c2, null)).extracting("code").doesNotContain(mine, global);
        // users and groups cannot be targeted from the global scope
        assertThatThrownBy(() -> temps.create(g, tmp(uniq("x"), null, null, end,
                new Audience("RESTRICTED", null, Set.of(u1.userId()), null))))
                .isInstanceOf(MenuDraftException.class).hasMessageContaining("tenant");
        assertThat(jdbc.queryForObject("SELECT status FROM (SELECT lifecycle_status AS status FROM menu_items WHERE code=?) s",
                String.class, global)).isEqualTo("ACTIVE");
    }

    @Test
    void temporaryMenuCanSitUnderAnyParentButIsAlwaysALeaf() {
        String org = UUID.randomUUID().toString();
        UUID t = tenant(uniq("tm8"), org);
        Person operator = person("superadmin", "x", org, t);
        AdminContext g = ctx(operator, ScopeKind.GLOBAL, null);
        AdminContext c = ctx(person("clinic_admin", "x", org, t), ScopeKind.TENANT, null);
        Person doc = person("doctor", "x", org, t);
        String gp = uniq("gp"), own = uniq("own"), kid = uniq("kid"), kid2 = uniq("kid2");
        Instant end = Instant.now().plus(Duration.ofHours(1));

        draft(g, put(gp, null, "Global parent", "/gp", "LINK", Set.of("DOCTOR")));
        publish.publish(g, null);
        draft(c, put(own, null, "Own parent", "/own", "LINK", Set.of("DOCTOR")));
        publish.publish(c, null);

        temps.create(c, tmp(kid, gp, null, end, roles("DOCTOR")));          // under a platform parent
        temps.create(c, tmp(kid2, own, null, end, roles("DOCTOR")));        // under the tenant's own parent

        var tree = menuQuery.menuFor(auth(doc), "PROVIDER_CONSOLE");
        assertThat(tree.stream().filter(e -> e.getId().equals(gp)).findFirst().orElseThrow().getChildren())
                .extracting(MenuEntry::getId).containsExactly(kid);
        assertThat(tree.stream().filter(e -> e.getId().equals(own)).findFirst().orElseThrow().getChildren())
                .extracting(MenuEntry::getId).containsExactly(kid2);
        assertThat(tree.stream().filter(e -> e.getId().equals(gp)).findFirst().orElseThrow().getChildren().get(0).getExpiresAt())
                .isNotNull();

        // a leaf parent keeps showing for a user outside the campaign's audience
        Person outsider = person("guest", "x", org, t);
        assertThat(sees(outsider, kid)).isFalse();

        // nothing can hang under a temporary menu, and drafts cannot reach one
        assertThatThrownBy(() -> temps.create(c, tmp(uniq("deep"), kid, null, end, roles("DOCTOR"))))
                .isInstanceOf(MenuDraftException.class).hasMessageContaining("temporary");
        assertThatThrownBy(() -> draft(c, put(uniq("p"), kid, "P", "/p", "LINK", Set.of())))
                .isInstanceOf(MenuDraftException.class).hasMessageContaining("temporary");
        assertThatThrownBy(() -> draft(c, MenuOp.builder().op(MenuOpType.PATCH_ITEM).code(kid).label("x").build()))
                .isInstanceOf(MenuDraftException.class).hasMessageContaining("temporary");
        assertThatThrownBy(() -> draft(c, MenuOp.builder().op(MenuOpType.DELETE_ITEM).code(kid).build()))
                .isInstanceOf(MenuDraftException.class);
    }

    @Test
    void publishAndRollbackNeverTouchTemporaryMenus() {
        String org = UUID.randomUUID().toString();
        UUID t = tenant(uniq("tm9"), org);
        AdminContext c = ctx(person("clinic_admin", "x", org, t), ScopeKind.TENANT, null);
        Person doc = person("doctor", "x", org, t);
        String perm = uniq("perm"), camp = uniq("camp");

        draft(c, put(perm, null, "Perm", "/perm", "LINK", Set.of("DOCTOR")));
        publish.publish(c, "v1");
        temps.create(c, tmp(camp, null, null, Instant.now().plus(Duration.ofHours(1)), roles("DOCTOR")));
        draft(c, MenuOp.builder().op(MenuOpType.PATCH_ITEM).code(perm).label("Renamed").build());
        publish.publish(c, "v2");

        publish.rollback(c, 1);
        publish.publish(c, "back to v1");

        assertThat(sees(doc, camp)).isTrue();               // the running campaign survived the rollback
        assertThat(jdbc.queryForObject("SELECT label FROM menu_items WHERE code=?", String.class, perm)).isEqualTo("Perm");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM menu_items WHERE code=? AND deleted_at IS NULL", Integer.class, camp)).isEqualTo(1);
    }

    @Test
    void overlappingMenusAllShowAndPriorityOnlyOrders() {
        String org = UUID.randomUUID().toString();
        UUID t = tenant(uniq("tm10"), org);
        AdminContext c = ctx(person("clinic_admin", "x", org, t), ScopeKind.TENANT, null);
        Person doc = person("doctor", "x", org, t);
        String a = uniq("a"), b = uniq("b");
        Instant end = Instant.now().plus(Duration.ofHours(1));

        temps.create(c, new TemporaryMenuRequest(a, a, null, null, "/same", null, null, null, 7, 1, null, null, end, null, null, null, roles("DOCTOR")));
        temps.create(c, new TemporaryMenuRequest(b, b, null, null, "/same", null, null, null, 7, 9, null, null, end, null, null, null, roles("DOCTOR")));

        List<String> order = codes(menuQuery.menuFor(auth(doc), "PROVIDER_CONSOLE")).stream()
                .filter(x -> x.equals(a) || x.equals(b)).toList();
        assertThat(order).containsExactly(b, a);            // both shown, higher priority first
    }

    @Test
    void tenantAdminWithoutAssignPrivilegeCannotTargetAnAudience() {
        String org = UUID.randomUUID().toString();
        UUID t = tenant(uniq("tm11"), org);
        AdminContext viewer = ctx(person("guest", "x", org, t), ScopeKind.TENANT, null);
        assertThatThrownBy(() -> temps.create(viewer, tmp(uniq("z"), null, null, Instant.now().plusSeconds(3600), roles("DOCTOR"))))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> temps.list(viewer, null)).isInstanceOf(AccessDeniedException.class);
    }
}
