package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.config.AppProperties;
import com.flowbinary.platformservice.config.JpaConfig;
import com.flowbinary.platformservice.dto.bootstrap.MenuEntry;
import com.flowbinary.platformservice.dto.menu.DraftOpsRequest;
import com.flowbinary.platformservice.dto.menu.DraftResponse;
import com.flowbinary.platformservice.dto.menu.PreviewRequest;
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

import java.util.*;

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
        MenuResolutionService.class, MenuQueryService.class, SettingsResolver.class, TenantContextResolver.class})
class MenuAdminIT {

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> System.getenv("MENU_IT_DB_URL"));
        r.add("spring.datasource.username", () -> Optional.ofNullable(System.getenv("MENU_IT_DB_USER")).orElse(System.getProperty("user.name")));
        r.add("spring.datasource.password", () -> Optional.ofNullable(System.getenv("MENU_IT_DB_PASSWORD")).orElse(""));
        r.add("spring.flyway.enabled", () -> "true");
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

        var result = publish.publish(g, "first");
        assertThat(result.version()).isEqualTo(1);
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
        assertThat(publish.versions(g)).extracting("version").contains(0, 1);
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

        assertThatThrownBy(() -> draft(c, MenuOp.builder().op(MenuOpType.PATCH_ITEM).code("dashboard").label("Hacked").build()))
                .isInstanceOf(MenuDraftException.class).hasMessageContaining("platform item");
        assertThatThrownBy(() -> draft(c, MenuOp.builder().op(MenuOpType.DELETE_ITEM).code("dashboard").build()))
                .isInstanceOf(MenuDraftException.class);
        assertThatThrownBy(() -> draft(c, MenuOp.builder().op(MenuOpType.SET_ROLES).code("dashboard").roles(Set.of("VIEWER")).build()))
                .isInstanceOf(MenuDraftException.class).hasMessageContaining("System roles");
        // unrelated rows untouched
        assertThat(jdbc.queryForObject("SELECT label FROM menu_items WHERE code='dashboard'", String.class))
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
        draft(c, MenuOp.builder().op(MenuOpType.SET_OVERRIDE).code("dashboard").label("Home Base").build());
        publish.publish(c, null);

        assertThat(menuQuery.menuFor(auth(doc1), "PROVIDER_CONSOLE")).extracting(MenuEntry::getLabel).contains("Home Base");
        assertThat(menuQuery.menuFor(auth(doc2), "PROVIDER_CONSOLE")).extracting(MenuEntry::getLabel).doesNotContain("Home Base");
        assertThat(jdbc.queryForObject("SELECT label FROM menu_items WHERE code='dashboard'", String.class)).isEqualTo("Dashboard");
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
        assertThatThrownBy(() -> draft(c, put("dashboard", null, "Dup", "/dup", "LINK", Set.of())))
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

        // DENY dashboard for the doctor
        draft(c, MenuOp.builder().op(MenuOpType.SET_USER_ACCESS).code("dashboard").userId(doc.userId())
                .effect("DENY").reason("test").build());
        publish.publish(c, null);
        assertThat(codes(menuQuery.menuFor(auth(doc), "PROVIDER_CONSOLE"))).doesNotContain("dashboard");

        // a user outside the tenant is rejected
        Person foreign = person("doctor", "x", UUID.randomUUID().toString(), tenant(uniq("n"), UUID.randomUUID().toString()));
        assertThatThrownBy(() -> draft(c, MenuOp.builder().op(MenuOpType.SET_USER_ACCESS).code("dashboard")
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
}
