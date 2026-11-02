package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.authz.sdk.PermissionCache;
import com.flowbinary.platformservice.authz.service.EffectivePermissionService;
import com.flowbinary.platformservice.authz.service.Operators;
import com.flowbinary.platformservice.authz.service.TenantFeatures;
import com.flowbinary.platformservice.config.AppProperties;
import com.flowbinary.platformservice.config.JpaConfig;
import com.flowbinary.platformservice.dto.bootstrap.MenuEntry;
import com.flowbinary.platformservice.dto.menu.DraftOpsRequest;
import com.flowbinary.platformservice.dto.module.ModuleRequest;
import com.flowbinary.platformservice.exception.MenuConflictException;
import com.flowbinary.platformservice.exception.MenuDraftException;
import com.flowbinary.platformservice.model.*;
import com.flowbinary.platformservice.security.ModuleIdentity;
import com.flowbinary.platformservice.security.TenantContextResolver;
import com.flowbinary.platformservice.service.MenuAdminAccess.AdminContext;
import com.flowbinary.platformservice.service.MenuAdminAccess.ScopeKind;
import com.flowbinary.platformservice.service.MenuItFixtures.Person;
import com.flowbinary.platformservice.service.MenuModuleSyncService.SyncResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.*;

import static com.flowbinary.platformservice.service.MenuItFixtures.uniq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Module registration and manifest sync against a real schema.
 * Opt-in like the other ITs: MENU_IT_DB_URL must point at an EMPTY scratch database.
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
        MenuCatalogueCache.class, MenuCatalogueCompiler.class, MenuMetrics.class, MenuCatalogEvents.class,
        MenuModuleSyncService.class, MenuModuleRegistryService.class, MenuModulePuller.class,
        MenuModulePullScheduler.class, ModuleIdentity.class})
class MenuModuleIT {

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> System.getenv("MENU_IT_DB_URL"));
        r.add("spring.datasource.username", () -> Optional.ofNullable(System.getenv("MENU_IT_DB_USER")).orElse(System.getProperty("user.name")));
        r.add("spring.datasource.password", () -> Optional.ofNullable(System.getenv("MENU_IT_DB_PASSWORD")).orElse(""));
        r.add("spring.flyway.enabled", () -> "true");
        r.add("app.menu.cache.version-check", () -> "1ms");
        // secure defaults: these tests assert that private/plain-http manifest URLs are refused
        r.add("app.menu.modules.allow-private-hosts", () -> "false");
        r.add("app.menu.modules.allow-insecure-http", () -> "false");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired MenuAdminAccess access;
    @Autowired MenuDraftService drafts;
    @Autowired MenuPublishService publish;
    @Autowired MenuQueryService menuQuery;
    @Autowired MenuCatalogueService catalogue;
    @Autowired MenuModuleRegistryService registry;
    @Autowired MenuModuleSyncService sync;
    @Autowired MenuModulePullScheduler scheduler;
    @Autowired ModuleIdentity identity;
    @Autowired MenuMetrics metrics;
    @Autowired com.flowbinary.platformservice.repository.MenuModuleRepository modules;

    MenuItFixtures fx;
    AdminContext operator;
    Person admin, guest;       // a clinic admin holds platform.subscription.view; a guest does not
    String code, client, prefix;

    @BeforeEach
    void setUp() {
        fx = new MenuItFixtures(jdbc);
        MenuItFixtures.ensureItems(jdbc);
        Object[] t = fx.tenant();
        UUID tenant = (UUID) t[0];
        String org = (String) t[1];
        Person op = fx.person("superadmin", tenant, org);
        operator = access.resolve(MenuItFixtures.user(op), "PROVIDER_CONSOLE", ScopeKind.GLOBAL, null);
        admin = fx.person("clinic_admin", tenant, org);
        guest = fx.person("guest", tenant, org);
        code = uniq("m");
        client = "client-" + code;
        prefix = "/" + code;
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private ModuleRequest request(String url) {
        return new ModuleRequest(code, "Module " + code, "d", "PROVIDER_CONSOLE", client, url, List.of(prefix), "billing");
    }

    private void activeModule() {
        registry.register(operator, request(null));
        registry.approve(operator, code);
    }

    private String item(String key, String parent, String label, String route, String type, String privilege) {
        StringBuilder sb = new StringBuilder("{\"key\":\"" + key + "\",\"label\":\"" + label + "\"");
        if (parent != null) sb.append(",\"parent\":\"").append(parent).append("\"");
        if (route != null) sb.append(",\"route\":\"").append(route).append("\"");
        if (type != null) sb.append(",\"type\":\"").append(type).append("\"");
        if (privilege != null) sb.append(",\"requiredPrivilege\":\"").append(privilege).append("\"");
        return sb.append("}").toString();
    }

    private String link(String key, String label) {
        return item(key, null, label, prefix + "/" + key, null, "platform.subscription.view");
    }

    private String manifest(String version, String... items) {
        return "{\"schemaVersion\":1,\"module\":\"" + code + "\",\"version\":\"" + version + "\",\"items\":["
                + String.join(",", items) + "]}";
    }

    private SyncResult push(String body) {
        String c = identity.requireOwner(MenuItFixtures.serviceToken(client), code);
        return sync.accept(code, body, "PUSH", c);
    }

    private List<String> codes(Person p) {
        List<String> out = new ArrayList<>();
        walk(menuQuery.menuFor(MenuItFixtures.user(p), "PROVIDER_CONSOLE"), out);
        return out;
    }

    private static void walk(List<MenuEntry> tree, List<String> out) {
        for (MenuEntry e : tree) {
            out.add(e.getId());
            if (e.getChildren() != null) walk(e.getChildren(), out);
        }
    }

    private String fullCode(String key) {
        return code + "." + key;
    }

    private void draftAndPublish(MenuOp... ops) {
        drafts.append(operator, new DraftOpsRequest(null, null, List.of(ops)));
        publish.publish(operator, null);
    }

    private long globalVersion() {
        return jdbc.queryForObject("SELECT COALESCE(MAX(version),0) FROM menu_catalog_version WHERE tenant_id IS NULL", Long.class);
    }

    private int historyRows(String outcome) {
        return jdbc.queryForObject("SELECT count(*) FROM menu_module_manifests h JOIN menu_modules m ON m.id=h.module_id WHERE m.code=? AND h.outcome=?",
                Integer.class, code, outcome);
    }

    // ── registration & identity ────────────────────────────────────────────

    @Test
    void aModuleMustBeApprovedBeforeItCanPublishAndOnlyTheOwnerClientMayPush() {
        registry.register(operator, request(null));
        assertThat(registry.get(operator, code).status()).isEqualTo("PENDING");

        assertThatThrownBy(() -> push(manifest("1", link("a", "A")))).isInstanceOf(MenuConflictException.class);

        registry.approve(operator, code);
        assertThat(push(manifest("1", link("a", "A"))).accepted()).isTrue();

        // someone else's service token, a user token of the module's own client, and an unknown module all get the same refusal
        assertThatThrownBy(() -> identity.requireOwner(MenuItFixtures.serviceToken("other-client"), code))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> identity.requireOwner(MenuItFixtures.userOfClient(client), code))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> identity.requireOwner(MenuItFixtures.serviceToken(client), "no-such-module"))
                .isInstanceOf(AccessDeniedException.class).hasMessage("Not permitted");
    }

    @Test
    void onlyOperatorsManageModulesAndRegistrationIsValidated() {
        AdminContext tenantAdmin = access.resolve(MenuItFixtures.user(admin), "PROVIDER_CONSOLE", ScopeKind.TENANT, null);
        assertThatThrownBy(() -> registry.register(tenantAdmin, request(null))).isInstanceOf(AccessDeniedException.class);

        registry.register(operator, request(null));
        assertThatThrownBy(() -> registry.register(operator, request(null))).isInstanceOf(MenuConflictException.class);   // code
        String other = uniq("m");
        assertThatThrownBy(() -> registry.register(operator,
                new ModuleRequest(other, "x", null, null, client, null, List.of("/" + other), null)))
                .isInstanceOf(MenuConflictException.class).hasMessageContaining("already owns");                            // client
        assertThatThrownBy(() -> registry.register(operator,
                new ModuleRequest(other, "x", null, null, "c-" + other, null, List.of(prefix + "/sub"), null)))
                .isInstanceOf(MenuDraftException.class).hasMessageContaining("overlaps");                                   // prefix
        assertThatThrownBy(() -> registry.register(operator,
                new ModuleRequest(other, "x", null, null, "c2-" + other, null, List.of("/"), null)))
                .isInstanceOf(MenuDraftException.class);
        assertThatThrownBy(() -> registry.register(operator,
                new ModuleRequest("Bad Code", "x", null, null, "c3-" + other, null, List.of("/zz" + other), null)))
                .isInstanceOf(MenuDraftException.class);
        assertThatThrownBy(() -> registry.register(operator,
                new ModuleRequest(other, "x", null, null, "c4-" + other, "http://127.0.0.1/m", List.of("/" + other), null)))
                .isInstanceOf(MenuDraftException.class).hasMessageContaining("https");                                     // SSRF
        assertThatThrownBy(() -> registry.register(operator,
                new ModuleRequest(other, "x", null, null, "c5-" + other, "https://169.254.169.254/m", List.of("/" + other), null)))
                .isInstanceOf(MenuDraftException.class).hasMessageContaining("private");
        assertThatThrownBy(() -> registry.register(operator,
                new ModuleRequest(other, "x", null, null, "c6-" + other, null, List.of("/" + other), "M99")))
                .isInstanceOf(MenuDraftException.class).hasMessageContaining("matches no service that owns privileges");
    }

    // ── publishing a menu ──────────────────────────────────────────────────

    @Test
    void anAcceptedManifestBecomesNamespacedGlobalItemsGatedByPrivilegeAlone() {
        activeModule();
        SyncResult r = push(manifest("2026.1",
                item("root", null, "Billing", null, "GROUP", null),
                item("invoices", "root", "Invoices", prefix + "/invoices", null, "platform.subscription.view")));

        assertThat(r.accepted()).isTrue();
        assertThat(r.created()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT source || ':' || coalesce(tenant_id::text,'global') || ':' || depth FROM menu_items WHERE code=?",
                String.class, fullCode("invoices"))).isEqualTo("MODULE:global:1");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM menu_plan_access WHERE menu_item_id=(SELECT id FROM menu_items WHERE code=?)",
                Integer.class, fullCode("invoices"))).isEqualTo(TenantPlan.values().length);

        assertThat(codes(admin)).contains(fullCode("root"), fullCode("invoices"));   // holds platform.subscription.view
        assertThat(codes(guest)).doesNotContain(fullCode("root"), fullCode("invoices"));
        assertThat(registry.get(operator, code).health()).isEqualTo("UP");
        assertThat(registry.get(operator, code).currentVersion()).isEqualTo("2026.1");
    }

    @Test
    void anInvalidManifestChangesNothingAndReportsEveryProblem() {
        activeModule();
        push(manifest("1", link("a", "A")));
        long before = globalVersion();

        SyncResult bad = push(manifest("2",
                item("a", null, "A2", "/elsewhere/x", null, "platform.subscription.view"),                        // outside prefix
                item("b", null, "B", prefix + "/b", null, null),                                    // no privilege
                item("c", null, "C", prefix + "/c", null, "lab.order.create"),                             // someone else's privilege
                item("d", null, "D", "https://evil.example", "EXTERNAL", "platform.subscription.view"),           // external
                item("e", "ghost", "E", prefix + "/e", null, "platform.subscription.view")));                     // unknown parent

        assertThat(bad.outcome()).isEqualTo(ManifestOutcome.REJECTED);
        assertThat(bad.errors().size()).isGreaterThanOrEqualTo(5);
        assertThat(bad.errors()).anyMatch(e -> e.contains("outside this module's registered prefixes"))
                .anyMatch(e -> e.contains("requiredPrivilege is required"))
                .anyMatch(e -> e.contains("belongs to another module"))
                .anyMatch(e -> e.contains("EXTERNAL"))
                .anyMatch(e -> e.contains("not an item of this manifest"));

        // the previous menu keeps serving, untouched
        assertThat(codes(admin)).contains(fullCode("a")).doesNotContain(fullCode("b"));
        assertThat(jdbc.queryForObject("SELECT label FROM menu_items WHERE code=?", String.class, fullCode("a"))).isEqualTo("A");
        assertThat(globalVersion()).isEqualTo(before);
        assertThat(registry.get(operator, code).health()).isEqualTo("STALE");
        assertThat(registry.get(operator, code).currentVersion()).isEqualTo("1");
        assertThat(registry.get(operator, code).consecutiveFailures()).isEqualTo(1);
        assertThat(historyRows("REJECTED")).isEqualTo(1);
        assertThat(registry.history(operator, code, 5).get(0).errors().size()).isGreaterThanOrEqualTo(5);

        // and a good manifest recovers it
        assertThat(push(manifest("3", link("a", "A3"))).accepted()).isTrue();
        assertThat(registry.get(operator, code).health()).isEqualTo("UP");
        assertThat(registry.get(operator, code).consecutiveFailures()).isZero();
    }

    @Test
    void malformedJsonAndUnknownFieldsAreRejectedNotCrashed() {
        activeModule();
        assertThat(push("{not json").outcome()).isEqualTo(ManifestOutcome.REJECTED);
        // an unknown field could have been a mistyped restriction, so it is an error
        SyncResult typo = push("{\"schemaVersion\":1,\"module\":\"" + code + "\",\"version\":\"1\",\"items\":[{\"key\":\"a\","
                + "\"label\":\"A\",\"route\":\"" + prefix + "/a\",\"requiredPriviledge\":\"platform.subscription.view\"}]}");
        assertThat(typo.outcome()).isEqualTo(ManifestOutcome.REJECTED);
        assertThat(typo.errors().get(0)).contains("requiredPriviledge");
        assertThat(registry.get(operator, code).health()).isEqualTo("DOWN");      // never delivered a valid manifest
    }

    @Test
    void routeAndCodeConflictsWithOtherOwnersAreRejected() {
        activeModule();
        MenuOp platform = MenuOp.builder().op(MenuOpType.PUT_ITEM).code(uniq("plat")).label("Platform item")
                .route(prefix + "/shared").build();
        draftAndPublish(platform);
        // drafts can no longer create codes in a module's namespace; a row from before that rule still can exist
        jdbc.update("INSERT INTO menu_items (id, application_id, code, label) "
                + "SELECT gen_random_uuid(), id, ?, 'Legacy' FROM applications WHERE code = 'PROVIDER_CONSOLE'", fullCode("shared"));

        SyncResult r = push(manifest("1", link("shared", "Mine"), link("ok", "OK")));
        assertThat(r.outcome()).isEqualTo(ManifestOutcome.REJECTED);
        assertThat(r.errors()).anyMatch(e -> e.contains("already belongs to the platform menu"))
                .anyMatch(e -> e.contains("already used by the platform menu"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM menu_items WHERE code=?", Integer.class, fullCode("ok"))).isZero();   // atomic
    }

    @Test
    void platformAreaCodesAndNamespacesAreReservedBothWays() {
        // a platform menu area
        assertThatThrownBy(() -> registry.register(operator,
                new ModuleRequest("billing", "x", null, null, "c-res-" + code, null, List.of(prefix), null)))
                .isInstanceOf(MenuConflictException.class).hasMessageContaining("reserved");

        // a code platform items already use as their namespace
        String area = uniq("area");
        draftAndPublish(MenuOp.builder().op(MenuOpType.PUT_ITEM).code(area + ".page").label("Page")
                .route("/" + area + "/page").build());
        assertThatThrownBy(() -> registry.register(operator,
                new ModuleRequest(area, "x", null, null, "c-area-" + code, null, List.of(prefix), null)))
                .isInstanceOf(MenuConflictException.class).hasMessageContaining("already used");

        // and the reverse: once a module is registered, drafts and temporary menus stay out of its namespace
        registry.register(operator, request(null));
        assertThatThrownBy(() -> drafts.append(operator, new DraftOpsRequest(null, null, List.of(
                MenuOp.builder().op(MenuOpType.PUT_ITEM).code(fullCode("sneaky")).label("S").route("/sneaky").build()))))
                .isInstanceOf(MenuDraftException.class).hasMessageContaining("namespace of module '" + code + "'");
    }

    // ── re-syncing ─────────────────────────────────────────────────────────

    @Test
    void identicalContentIsACheapNoOpEvenIfFieldOrderDiffers() {
        activeModule();
        push(manifest("1", link("a", "A")));
        long before = globalVersion();
        int rows = historyRows("ACCEPTED");

        String reordered = "{\"items\":[{\"requiredPrivilege\":\"platform.subscription.view\",\"route\":\"" + prefix + "/a\",\"label\":\"A\",\"key\":\"a\"}],"
                + "\"version\":\"1\",\"module\":\"" + code + "\",\"schemaVersion\":1}";
        SyncResult r = push(reordered);

        assertThat(r.accepted()).isTrue();
        assertThat(r.changed()).isFalse();
        assertThat(globalVersion()).isEqualTo(before);                 // no cache invalidation
        assertThat(historyRows("ACCEPTED")).isEqualTo(rows);           // no history spam
    }

    @Test
    void aSyncKeepsWhatAdministratorsDecidedAndRestoresDroppedItemsWithTheirSettings() {
        activeModule();
        push(manifest("1", link("a", "A"), link("b", "B")));

        // the operator hides b and moves a, through a normal draft
        draftAndPublish(
                MenuOp.builder().op(MenuOpType.PATCH_ITEM).code(fullCode("b")).enabled(false).build(),
                MenuOp.builder().op(MenuOpType.PATCH_ITEM).code(fullCode("a")).sortOrder(77).build());
        assertThat(codes(admin)).contains(fullCode("a")).doesNotContain(fullCode("b"));

        // the module changes a's label, drops b, adds c
        SyncResult r = push(manifest("2", link("a", "A renamed"), link("c", "C")));
        assertThat(r.created()).isEqualTo(1);
        assertThat(r.updated()).isEqualTo(1);
        assertThat(r.removed()).isEqualTo(1);

        assertThat(jdbc.queryForObject("SELECT label FROM menu_items WHERE code=?", String.class, fullCode("a"))).isEqualTo("A renamed");
        assertThat(jdbc.queryForObject("SELECT sort_order FROM menu_items WHERE code=?", Integer.class, fullCode("a"))).isEqualTo(77);   // admin's order kept
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM menu_items WHERE code=?", Boolean.class, fullCode("b"))).isTrue();

        // b comes back: still hidden, because that was the administrator's call
        push(manifest("3", link("a", "A renamed"), link("b", "B again")));
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NULL AND NOT is_active FROM menu_items WHERE code=?", Boolean.class, fullCode("b"))).isTrue();
        assertThat(codes(admin)).doesNotContain(fullCode("b"));
    }

    @Test
    void aModuleCannotReachItemsOutsideItsNamespaceEvenByGuessingCodes() {
        activeModule();
        SyncResult r = push(manifest("1", link("fx_dash", "Hijack")));          // code would be "<module>.fx_dash", never "fx_dash"
        assertThat(r.accepted()).isTrue();
        assertThat(jdbc.queryForObject("SELECT label FROM menu_items WHERE code='fx_dash'", String.class)).isEqualTo("Fixture dashboard");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM menu_items WHERE module_id=(SELECT id FROM menu_modules WHERE code=?) AND code NOT LIKE ?",
                Integer.class, code, code + ".%")).isZero();
    }

    // ── what administrators may do to a module's items ─────────────────────

    @Test
    void administratorsMayHideAndReorderModuleItemsButNotEditThem() {
        activeModule();
        push(manifest("1", item("root", null, "Root", null, "GROUP", null),
                item("a", "root", "A", prefix + "/a", null, "platform.subscription.view")));
        String a = fullCode("a"), root = fullCode("root");

        for (MenuOp forbidden : new MenuOp[]{
                MenuOp.builder().op(MenuOpType.PATCH_ITEM).code(a).label("Hacked").build(),
                MenuOp.builder().op(MenuOpType.PATCH_ITEM).code(a).route("/x").build(),
                MenuOp.builder().op(MenuOpType.DELETE_ITEM).code(a).build(),
                MenuOp.builder().op(MenuOpType.MOVE_ITEM).code(a).build(),
                MenuOp.builder().op(MenuOpType.SET_USER_ACCESS).code(a).userId(UUID.randomUUID()).effect("GRANT").build(),
                MenuOp.builder().op(MenuOpType.PUT_ITEM).code(a).label("Replace").route("/x").build(),
                MenuOp.builder().op(MenuOpType.PUT_ITEM).code(uniq("p")).label("Under module").route("/p").parentCode(root).build()}) {
            assertThatThrownBy(() -> drafts.append(operator, new DraftOpsRequest(null, null, List.of(forbidden))))
                    .as(forbidden.op().name()).isInstanceOf(MenuDraftException.class);
        }
        assertThat(drafts.current(operator)).isEmpty();                              // nothing was stored

        draftAndPublish(MenuOp.builder().op(MenuOpType.SET_ENABLED).code(a).enabled(false).build());
        assertThat(codes(admin)).doesNotContain(a);
        draftAndPublish(MenuOp.builder().op(MenuOpType.SET_ENABLED).code(a).enabled(true).build(),
                MenuOp.builder().op(MenuOpType.REORDER).parentCode(root).orderedCodes(List.of(a)).build());
        assertThat(codes(admin)).contains(a);

        var shown = catalogue.catalogue(operator).stream().filter(c -> c.code().equals(a)).findFirst().orElseThrow();
        assertThat(shown.module()).isEqualTo(code);
        assertThat(shown.editable()).isFalse();
    }

    @Test
    void aTenantAdminCanHideOrReorderAModuleItemForTheirTenantOnly() {
        activeModule();
        push(manifest("1", link("a", "A")));
        String a = fullCode("a");
        Person otherTenantAdmin = fx.person("clinic_admin");
        AdminContext tenantCtx = access.resolve(MenuItFixtures.user(admin), "PROVIDER_CONSOLE", ScopeKind.TENANT, null);

        drafts.append(tenantCtx, new DraftOpsRequest(null, null, List.of(MenuOp.builder().op(MenuOpType.SET_ENABLED).code(a).enabled(false).build())));
        publish.publish(tenantCtx, null);

        assertThat(codes(admin)).doesNotContain(a);
        assertThat(codes(otherTenantAdmin)).contains(a);
        assertThat(jdbc.queryForObject("SELECT is_active FROM menu_items WHERE code=?", Boolean.class, a)).isTrue();   // platform row untouched
    }

    @Test
    void rollingBackADraftRestoresHideAndOrderButNeverUndoesTheModulesOwnChanges() {
        activeModule();
        push(manifest("1", link("a", "A")));
        String a = fullCode("a");
        draftAndPublish(MenuOp.builder().op(MenuOpType.SET_ENABLED).code(a).enabled(false).build());       // vN: hidden
        int hidden = publish.versions(operator).get(0).version();
        draftAndPublish(MenuOp.builder().op(MenuOpType.SET_ENABLED).code(a).enabled(true).build());        // vN+1: shown
        push(manifest("2", link("a", "A renamed")));                                                       // module's own change

        publish.rollback(operator, hidden);
        publish.publish(operator, "back to hidden");

        assertThat(jdbc.queryForObject("SELECT is_active FROM menu_items WHERE code=?", Boolean.class, a)).isFalse();
        assertThat(jdbc.queryForObject("SELECT label FROM menu_items WHERE code=?", String.class, a)).isEqualTo("A renamed");
    }

    // ── state machine ──────────────────────────────────────────────────────

    @Test
    void suspendHidesAndRefusesResumeRestoresAndDeregisterRemovesAndReservesTheCode() {
        activeModule();
        push(manifest("1", link("a", "A")));
        String a = fullCode("a");
        assertThat(codes(admin)).contains(a);

        registry.suspend(operator, code);
        assertThat(codes(admin)).doesNotContain(a);
        assertThatThrownBy(() -> push(manifest("2", link("a", "A")))).isInstanceOf(MenuConflictException.class);

        registry.resume(operator, code);
        assertThat(codes(admin)).contains(a);

        registry.deregister(operator, code);
        assertThat(codes(admin)).doesNotContain(a);
        assertThat(registry.get(operator, code).status()).isEqualTo("DEREGISTERED");
        assertThatThrownBy(() -> registry.approve(operator, code)).isInstanceOf(MenuConflictException.class);
        assertThatThrownBy(() -> registry.register(operator, request(null))).isInstanceOf(MenuConflictException.class);   // code reserved
        // the client is free for a new module
        String next = uniq("m");
        registry.register(operator, new ModuleRequest(next, "n", null, null, client, null, List.of("/" + next), null));
    }

    @Test
    void everyLifecycleStepAndDeliveryIsAudited() {
        activeModule();
        push(manifest("1", link("a", "A")));
        push("{broken");
        registry.suspend(operator, code);

        List<String> actions = jdbc.queryForList("SELECT action FROM audit_log WHERE entity_type='MENU_MODULE' AND entity_id=? ORDER BY occurred_at",
                String.class, code);
        assertThat(actions).contains("MENU_MODULE_REGISTERED", "MENU_MODULE_APPROVED", "MENU_MODULE_MANIFEST_ACCEPTED",
                "MENU_MODULE_MANIFEST_REJECTED", "MENU_MODULE_SUSPENDED");
        assertThat(jdbc.queryForObject("SELECT actor_type || ':' || (metadata->>'clientId') FROM audit_log WHERE action='MENU_MODULE_MANIFEST_ACCEPTED' AND entity_id=?",
                String.class, code)).isEqualTo("SERVICE:" + client);
        assertThat(metrics.registry().counter("menu.module.sync", "module", code, "outcome", "ACCEPTED").count()).isEqualTo(1);
        assertThat(metrics.registry().get("menu.module.health").tag("module", code).gauge().value()).isEqualTo(1);   // STALE after the bad push
    }

    // ── concurrency & scheduling ───────────────────────────────────────────

    @Test
    void concurrentPushesOfOneModuleSerialiseAndLeaveAConsistentMenu() throws Exception {
        activeModule();
        String m1 = manifest("1", link("one", "One"));
        String m2 = manifest("2", link("two", "Two"));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<SyncResult>> futures = new ArrayList<>();
        for (String body : List.of(m1, m2)) {
            futures.add(pool.submit(() -> {
                go.await();
                return push(body);
            }));
        }
        go.countDown();
        for (Future<SyncResult> f : futures) assertThat(f.get(30, TimeUnit.SECONDS).accepted()).isTrue();
        pool.shutdown();

        // whichever ran last wins, and the live items are exactly its items
        String version = registry.get(operator, code).currentVersion();
        List<String> live = jdbc.queryForList("SELECT code FROM menu_items WHERE module_id=(SELECT id FROM menu_modules WHERE code=?) AND deleted_at IS NULL",
                String.class, code);
        assertThat(live).containsExactly(fullCode(version.equals("1") ? "one" : "two"));
    }

    @Test
    void theScheduleLeasesADueModuleToExactlyOneInstance() throws Exception {
        registry.register(operator, new ModuleRequest(code, "n", null, null, client, "https://93.184.216.34/menu", List.of(prefix), null));
        registry.approve(operator, code);      // approving a module with a URL makes it due at once
        assertThat(registry.get(operator, code).nextAttemptAt()).isNotNull();

        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Future<List<String>>> claims = new ArrayList<>();
        CountDownLatch go = new CountDownLatch(1);
        for (int i = 0; i < 4; i++) {
            claims.add(pool.submit(() -> {
                go.await();
                return scheduler.claimDue(java.time.Instant.now());
            }));
        }
        go.countDown();
        long claimedMine = 0;
        for (Future<List<String>> f : claims) claimedMine += f.get(30, TimeUnit.SECONDS).stream().filter(code::equals).count();
        pool.shutdown();

        assertThat(claimedMine).isEqualTo(1);
        assertThat(scheduler.claimDue(java.time.Instant.now())).doesNotContain(code);   // leased for minutes
    }
}
