package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.dto.module.MenuManifest;
import com.flowbinary.platformservice.dto.module.MenuManifest.Item;
import com.flowbinary.platformservice.model.MenuModule;
import com.flowbinary.platformservice.model.MenuPlacement;
import com.flowbinary.platformservice.authz.model.AuthzPrivilege;
import com.flowbinary.platformservice.service.MenuManifestValidator.Refs;
import com.flowbinary.platformservice.service.MenuManifestValidator.Result;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class MenuManifestValidatorTest {

    private final MenuModule module = MenuModule.builder().code("billing")
            .allowedRoutePrefixes(List.of("/billing")).privilegeModuleCode("billing").build();

    /** Privileges: billing.invoice.view (the module's own service), platform.dashboard.view (shared), lab.order.create (another service's). */
    private final Map<String, AuthzPrivilege> privileges = new HashMap<>();
    private final Map<String, String> routeOwners = new HashMap<>();
    private final Map<String, String> codeOwners = new HashMap<>();
    private final Set<String> features = new HashSet<>(Set.of("billing"));

    private final Refs refs = new Refs() {
        public Optional<AuthzPrivilege> privilege(String c) { return Optional.ofNullable(privileges.get(c)); }
        public boolean featureExists(String c) { return features.contains(c); }
        public Optional<String> routeOwner(String r) { return Optional.ofNullable(routeOwners.get(r)); }
        public Optional<String> codeOwner(String c) { return Optional.ofNullable(codeOwners.get(c)); }
    };

    MenuManifestValidatorTest() {
        privileges.put("billing.invoice.view", priv("billing.invoice.view"));
        privileges.put("platform.dashboard.view", priv("platform.dashboard.view"));
        privileges.put("lab.order.create", priv("lab.order.create"));
    }

    private static AuthzPrivilege priv(String code) {
        AuthzPrivilege p = new AuthzPrivilege();
        p.setCode(code);
        p.setOwnerService(code.substring(0, code.indexOf('.')));
        p.setName(code);
        return p;
    }

    private static Item link(String key, String parent, String route) {
        return new Item(key, parent, key, null, null, route, null, null, null, null, null, null, null, "billing.invoice.view", null, null);
    }

    private static Item group(String key, String parent) {
        return new Item(key, parent, key, null, null, null, "GROUP", null, null, null, null, null, null, null, null, null);
    }

    private Result check(Item... items) {
        return MenuManifestValidator.validate(module, new MenuManifest(1, "billing", "1.0", List.of(items)), 4, refs);
    }

    private void rejects(Result r, String fragment) {
        assertThat(r.ok()).isFalse();
        assertThat(r.errors()).anyMatch(e -> e.contains(fragment));
        assertThat(r.items()).isEmpty();
    }

    @Test
    void placementAndTenantTypesDefaultAndAreChecked() {
        Item plain = link("a", null, "/billing/a");
        Item placed = new Item("b", null, "B", null, null, "/billing/b", null, null, null, "SETTINGS",
                List.of("STORE", "LAB"), null, null, "billing.invoice.view", null, null);
        Result ok = check(plain, placed);
        assertThat(ok.errors()).isEmpty();
        assertThat(ok.items().get(0).placement()).isEqualTo(MenuPlacement.LEFT);
        assertThat(ok.items().get(0).tenantTypes()).isNull();
        assertThat(ok.items().get(1).placement()).isEqualTo(MenuPlacement.SETTINGS);
        assertThat(ok.items().get(1).tenantTypes()).containsExactly("LAB", "STORE");

        rejects(check(new Item("c", null, "C", null, null, "/billing/c", null, null, null, "SIDEBAR",
                null, null, null, "billing.invoice.view", null, null)), "placement");
        rejects(check(new Item("c", null, "C", null, null, "/billing/c", null, null, null, null,
                List.of("HOSPITAL"), null, null, "billing.invoice.view", null, null)), "tenantTypes");
    }

    @Test
    void acceptsAValidManifestAndNamespacesTheCodes() {
        Result r = check(group("root", null), link("invoices", "root", "/billing/invoices?x=1"));
        assertThat(r.errors()).isEmpty();
        assertThat(r.items()).extracting("code").containsExactly("billing.root", "billing.invoices");
        assertThat(r.items().get(1).privilege().getCode()).isEqualTo("billing.invoice.view");
    }

    @Test
    void acceptsSharedPlatformPrivilegesButNotAnotherModulesPrivileges() {
        Item shared = new Item("a", null, "A", null, null, "/billing/a", null, null, null, null, null, null, null, "platform.dashboard.view", null, null);
        assertThat(check(shared).ok()).isTrue();
        Item foreign = new Item("a", null, "A", null, null, "/billing/a", null, null, null, null, null, null, null, "lab.order.create", null, null);
        rejects(check(foreign), "belongs to another module");
        Item missing = new Item("a", null, "A", null, null, "/billing/a", null, null, null, null, null, null, null, "nope.nope", null, null);
        rejects(check(missing), "does not exist");
    }

    @Test
    void everyLinkMustDeclareAPrivilegeBecauseItIsTheOnlyGate() {
        Item open = new Item("a", null, "A", null, null, "/billing/a", null, null, null, null, null, null, null, null, null, null);
        rejects(check(open), "requiredPrivilege is required");
        assertThat(check(group("g", null)).ok()).isTrue();     // a group is shown only through its children
    }

    @Test
    void routesMustStayInsideTheModulesPrefixes() {
        for (String bad : new String[]{"/other/x", "/billingx", "/billing-evil/x", "/", "//evil.example/billing", "javascript:1", "billing/x"}) {
            rejects(check(link("a", null, bad)), "route");
        }
        for (String ok : new String[]{"/billing", "/billing/x", "/billing/x/y?z=1#top"}) {
            assertThat(check(link("a", null, ok)).ok()).as(ok).isTrue();
        }
    }

    @Test
    void routeConflictsWithOtherOwnersAreRejectedButItsOwnRoutesAreFine() {
        routeOwners.put("/billing/taken", "module 'other'");
        rejects(check(link("a", null, "/billing/taken")), "already used by module 'other'");
        rejects(check(link("a", null, "/billing/x"), link("b", null, "/billing/x")), "another item of this manifest");
    }

    @Test
    void codesOwnedByAnotherOwnerAreRejected() {
        codeOwners.put("billing.a", "the platform menu");
        rejects(check(link("a", null, "/billing/a")), "already belongs to the platform menu");
    }

    @Test
    void externalLinksAreNotAllowed() {
        Item ext = new Item("a", null, "A", null, null, "https://evil.example", "EXTERNAL", null, null, null, null, null, null, "billing.invoice.view", null, null);
        rejects(check(ext), "EXTERNAL is not allowed");
    }

    @Test
    void treeProblemsAreCaught() {
        rejects(check(link("a", "ghost", "/billing/a")), "not an item of this manifest");
        rejects(check(group("a", "b"), group("b", "a")), "cycle");
        Item div = new Item("d", null, "-", null, null, null, "DIVIDER", null, null, null, null, null, null, null, null, null);
        rejects(check(div, link("a", "d", "/billing/a")), "DIVIDER and cannot have children");
        rejects(check(group("l0", null), group("l1", "l0"), group("l2", "l1"), group("l3", "l2"), group("l4", "l3")), "levels deep");
        assertThat(check(group("l0", null), group("l1", "l0"), group("l2", "l1"), group("l3", "l2")).ok()).isTrue();   // 4 levels
    }

    @Test
    void duplicateKeysAndBadKeysAreRejected() {
        rejects(check(link("a", null, "/billing/a"), link("a", null, "/billing/b")), "used more than once");
        rejects(check(link("Bad Key", null, "/billing/a")), "key must be");
        rejects(check(link("a".repeat(41), null, "/billing/a")), "key must be");
    }

    @Test
    void headerFieldsAreChecked() {
        assertThat(MenuManifestValidator.validate(module, new MenuManifest(2, "billing", "1", List.of()), 4, refs).errors())
                .anyMatch(e -> e.contains("schemaVersion"));
        assertThat(MenuManifestValidator.validate(module, new MenuManifest(1, "someone-else", "1", List.of()), 4, refs).errors())
                .anyMatch(e -> e.contains("module must be 'billing'"));
        assertThat(MenuManifestValidator.validate(module, new MenuManifest(1, "billing", " ", List.of()), 4, refs).errors())
                .anyMatch(e -> e.contains("version"));
        assertThat(MenuManifestValidator.validate(module, null, 4, refs).ok()).isFalse();
    }

    @Test
    void tooManyItemsAreRejected() {
        List<Item> many = new ArrayList<>();
        for (int i = 0; i <= MenuManifestValidator.MAX_ITEMS; i++) many.add(group("g" + i, null));
        assertThat(MenuManifestValidator.validate(module, new MenuManifest(1, "billing", "1", many), 4, refs).errors())
                .anyMatch(e -> e.contains("at most 200"));
    }

    @Test
    void anEmptyManifestIsValidAndMeansNoMenu() {
        assertThat(check().ok()).isTrue();
    }

    @Test
    void featuresIconsAndTranslationsAreChecked() {
        Item badFeature = new Item("a", null, "A", null, null, "/billing/a", null, null, null, null, null, null, null, "billing.invoice.view", "ghost", null);
        rejects(check(badFeature), "requiredFeature");
        Item okFeature = new Item("a", null, "A", null, null, "/billing/a", null, null, null, null, null, null, null, "billing.invoice.view", "billing", null);
        assertThat(check(okFeature).ok()).isTrue();
        Item badIcon = new Item("a", null, "A", null, "<img>", "/billing/a", null, null, null, null, null, null, null, "billing.invoice.view", null, null);
        rejects(check(badIcon), "icon");
        Item badLocale = new Item("a", null, "A", null, null, "/billing/a", null, null, null, null, null, null, null, "billing.invoice.view", null, Map.of("EN_us!", "x"));
        rejects(check(badLocale), "bad locale");
    }

    @Test
    void everyProblemIsReportedNotJustTheFirst() {
        Result r = check(link("a", "ghost", "/elsewhere"), new Item("b", null, "", null, null, "/billing/b", null, null, null, null, null, null, null, "nope", null, null));
        assertThat(r.errors().size()).isGreaterThanOrEqualTo(4);
    }

    @Test
    void prefixRulesAndOverlap() {
        assertThat(MenuManifestValidator.checkPrefixes(List.of("/billing", "/lab/results"))).isEmpty();
        for (String bad : new String[]{"/", "billing", "/Billing", "/billing/", "//x", "/a b"}) {
            assertThat(MenuManifestValidator.checkPrefixes(List.of(bad))).as(bad).isNotEmpty();
        }
        assertThat(MenuManifestValidator.checkPrefixes(List.of())).isNotEmpty();
        assertThat(MenuManifestValidator.prefixesOverlap("/billing", "/billing/reports")).isTrue();
        assertThat(MenuManifestValidator.prefixesOverlap("/billing", "/billing")).isTrue();
        assertThat(MenuManifestValidator.prefixesOverlap("/billing", "/billingx")).isFalse();   // segment boundary
    }
}
