package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.model.*;
import com.flowbinary.platformservice.service.MenuVisibilityPolicy.Subject;
import com.flowbinary.platformservice.service.MenuVisibilityPolicy.Verdict;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.flowbinary.platformservice.service.MenuTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

class MenuVisibilityPolicyTest {

    private final Role doctor = role("DOCTOR");

    private Subject subject(Map<UUID, MenuAccessEffect> user, Map<UUID, CatalogOverride> tenant,
                            boolean superadmin, Set<String> privileges, Set<String> features, String plan) {
        return new Subject(Set.of(doctor.getId()), privileges, superadmin, "CLINIC", plan, features, user, tenant, Set.of(), Set.of(), java.time.Instant.now());
    }

    private Subject plain() {
        return subject(Map.of(), Map.of(), false, Set.of(), Set.of(), "pro");
    }

    // ── Tenant-type gate ────────────────────────────────────────────────────

    private Subject ofType(String tenantType, boolean superadmin, Map<UUID, MenuAccessEffect> user) {
        return new Subject(Set.of(doctor.getId()), Set.of(), superadmin, tenantType, "pro", Set.of(), user, Map.of(),
                Set.of(), Set.of(), java.time.Instant.now());
    }

    @Test
    void itemWithoutTenantTypesIsShownToEveryType() {
        MenuItem m = open("a", null, doctor);
        assertThat(MenuVisibilityPolicy.evaluate(CatalogItem.of(m), ofType("LAB", false, Map.of()))).isEqualTo(Verdict.VISIBLE);
        assertThat(MenuVisibilityPolicy.evaluate(CatalogItem.of(m), ofType(null, false, Map.of()))).isEqualTo(Verdict.VISIBLE);
    }

    @Test
    void tenantTypeGateHidesItemFromOtherTypes() {
        MenuItem m = open("charting", null, doctor);
        m.setTenantTypes(java.util.List.of("CLINIC", "PRIVATE_CLINIC"));
        assertThat(MenuVisibilityPolicy.evaluate(CatalogItem.of(m), ofType("PRIVATE_CLINIC", false, Map.of()))).isEqualTo(Verdict.VISIBLE);
        assertThat(MenuVisibilityPolicy.evaluate(CatalogItem.of(m), ofType("LAB", false, Map.of()))).isEqualTo(Verdict.TENANT_TYPE);
    }

    @Test
    void unknownTenantTypePassesOnlyUngatedItems() {
        MenuItem m = open("a", null, doctor);
        m.setTenantTypes(java.util.List.of("CLINIC"));
        assertThat(MenuVisibilityPolicy.evaluate(CatalogItem.of(m), ofType(null, false, Map.of()))).isEqualTo(Verdict.TENANT_TYPE);
    }

    @Test
    void tenantTypeIsMandatoryEvenForSuperadminAndUserGrant() {
        MenuItem m = open("dispense", null, doctor);
        m.setTenantTypes(java.util.List.of("STORE"));
        assertThat(MenuVisibilityPolicy.evaluate(CatalogItem.of(m), ofType("CLINIC", true, Map.of()))).isEqualTo(Verdict.TENANT_TYPE);
        assertThat(MenuVisibilityPolicy.evaluate(CatalogItem.of(m),
                ofType("CLINIC", false, Map.of(m.getId(), MenuAccessEffect.GRANT)))).isEqualTo(Verdict.TENANT_TYPE);
    }

    @Test
    void roleGrantMakesItemVisible() {
        MenuItem m = open("a", null, doctor);
        assertThat(MenuVisibilityPolicy.evaluate(CatalogItem.of(m), plain())).isEqualTo(Verdict.VISIBLE);
    }

    @Test
    void noGrantMeansHidden() {
        MenuItem m = withPlans(item("a", null), TenantPlan.values());
        assertThat(MenuVisibilityPolicy.evaluate(CatalogItem.of(m), plain())).isEqualTo(Verdict.NOT_GRANTED);
    }

    @Test
    void userDenyBeatsRoleGrant() {
        MenuItem m = open("a", null, doctor);
        Subject s = subject(Map.of(m.getId(), MenuAccessEffect.DENY), Map.of(), false, Set.of(), Set.of(), "pro");
        assertThat(MenuVisibilityPolicy.evaluate(CatalogItem.of(m), s)).isEqualTo(Verdict.USER_DENIED);
    }

    @Test
    void userGrantAddsItemWithoutRole() {
        MenuItem m = withPlans(item("a", null), TenantPlan.values());
        Subject s = subject(Map.of(m.getId(), MenuAccessEffect.GRANT), Map.of(), false, Set.of(), Set.of(), "pro");
        assertThat(MenuVisibilityPolicy.evaluate(CatalogItem.of(m), s)).isEqualTo(Verdict.VISIBLE);
    }

    @Test
    void userGrantCannotBypassPlan() {
        MenuItem m = withPlans(item("a", null), TenantPlan.enterprise);
        Subject s = subject(Map.of(m.getId(), MenuAccessEffect.GRANT), Map.of(), false, Set.of(), Set.of(), "free");
        assertThat(MenuVisibilityPolicy.evaluate(CatalogItem.of(m), s)).isEqualTo(Verdict.PLAN);
    }

    @Test
    void userGrantCannotBypassFeature() {
        MenuItem m = open("a", null, doctor);
        m.setRequiredFeature("ai");
        Subject s = subject(Map.of(m.getId(), MenuAccessEffect.GRANT), Map.of(), false, Set.of(), Set.of(), "pro");
        assertThat(MenuVisibilityPolicy.evaluate(CatalogItem.of(m), s)).isEqualTo(Verdict.FEATURE);
    }

    @Test
    void userGrantCannotBypassPrivilege() {
        MenuItem m = withPlans(item("a", null), TenantPlan.values());
        m.setRequiredPrivilege(privilege("user.manage"));
        Subject s = subject(Map.of(m.getId(), MenuAccessEffect.GRANT), Map.of(), false, Set.of(), Set.of(), "pro");
        assertThat(MenuVisibilityPolicy.evaluate(CatalogItem.of(m), s)).isEqualTo(Verdict.PRIVILEGE);
    }

    @Test
    void superadminBypassesPrivilegeAndGrantButNotPlanOrDeny() {
        MenuItem m = withPlans(item("a", null), TenantPlan.pro);
        m.setRequiredPrivilege(privilege("user.manage"));
        assertThat(MenuVisibilityPolicy.evaluate(CatalogItem.of(m), subject(Map.of(), Map.of(), true, Set.of(), Set.of(), "pro")))
                .isEqualTo(Verdict.VISIBLE);
        assertThat(MenuVisibilityPolicy.evaluate(CatalogItem.of(m), subject(Map.of(), Map.of(), true, Set.of(), Set.of(), "free")))
                .isEqualTo(Verdict.PLAN);
        assertThat(MenuVisibilityPolicy.evaluate(CatalogItem.of(m),
                subject(Map.of(m.getId(), MenuAccessEffect.DENY), Map.of(), true, Set.of(), Set.of(), "pro")))
                .isEqualTo(Verdict.USER_DENIED);
    }

    @Test
    void tenantHideBeatsUserGrant() {
        MenuItem m = withPlans(item("a", null), TenantPlan.values());
        CatalogOverride hide = new CatalogOverride(null, null, null, true);
        Subject s = subject(Map.of(m.getId(), MenuAccessEffect.GRANT), Map.of(m.getId(), hide),
                false, Set.of(), Set.of(), "pro");
        assertThat(MenuVisibilityPolicy.evaluate(CatalogItem.of(m), s)).isEqualTo(Verdict.TENANT_HIDDEN);
    }

    @Test
    void notInNavigationIsHidden() {
        MenuItem m = open("a", null, doctor);
        m.setIsVisible(false);
        assertThat(MenuVisibilityPolicy.evaluate(CatalogItem.of(m), plain())).isEqualTo(Verdict.NOT_IN_NAVIGATION);
    }

    private Subject withGroups(Map<UUID, MenuAccessEffect> user, Set<UUID> denied, Set<UUID> granted) {
        return new Subject(Set.of(doctor.getId()), Set.of(), false, "CLINIC", "pro", Set.of(), user, Map.of(), denied, granted, java.time.Instant.now());
    }

    @Test
    void groupGrantMakesItemVisibleWithoutRole() {
        MenuItem m = withPlans(item("a", null), TenantPlan.values());
        assertThat(MenuVisibilityPolicy.evaluate(CatalogItem.of(m), withGroups(Map.of(), Set.of(), Set.of(m.getId()))))
                .isEqualTo(Verdict.VISIBLE);
    }

    @Test
    void groupDenyBeatsRoleGrantAndUserGrant() {
        MenuItem m = open("a", null, doctor);
        assertThat(MenuVisibilityPolicy.evaluate(CatalogItem.of(m), withGroups(Map.of(), Set.of(m.getId()), Set.of())))
                .isEqualTo(Verdict.GROUP_DENIED);
        assertThat(MenuVisibilityPolicy.evaluate(CatalogItem.of(m),
                withGroups(Map.of(m.getId(), MenuAccessEffect.GRANT), Set.of(m.getId()), Set.of())))
                .isEqualTo(Verdict.GROUP_DENIED);
    }

    @Test
    void groupDenyBeatsGroupGrantFromAnotherGroup() {
        MenuItem m = withPlans(item("a", null), TenantPlan.values());
        assertThat(MenuVisibilityPolicy.evaluate(CatalogItem.of(m), withGroups(Map.of(), Set.of(m.getId()), Set.of(m.getId()))))
                .isEqualTo(Verdict.GROUP_DENIED);
    }

    @Test
    void userDenyBeatsGroupGrant() {
        MenuItem m = withPlans(item("a", null), TenantPlan.values());
        assertThat(MenuVisibilityPolicy.evaluate(CatalogItem.of(m),
                withGroups(Map.of(m.getId(), MenuAccessEffect.DENY), Set.of(), Set.of(m.getId()))))
                .isEqualTo(Verdict.USER_DENIED);
    }

    @Test
    void groupGrantCannotBypassMandatoryGates() {
        MenuItem m = withPlans(item("a", null), TenantPlan.enterprise);
        assertThat(MenuVisibilityPolicy.evaluate(CatalogItem.of(m), withGroups(Map.of(), Set.of(), Set.of(m.getId()))))
                .isEqualTo(Verdict.PLAN);
        MenuItem p = withPlans(item("b", null), TenantPlan.values());
        p.setRequiredPrivilege(privilege("user.manage"));
        assertThat(MenuVisibilityPolicy.evaluate(CatalogItem.of(p), withGroups(Map.of(), Set.of(), Set.of(p.getId()))))
                .isEqualTo(Verdict.PRIVILEGE);
    }

    // ── temporary menus ────────────────────────────────────────────────────

    private static final java.time.Instant T0 = java.time.Instant.parse("2026-11-01T10:00:00Z");

    private MenuItem temp(MenuLifecycle status, String starts, String ends, MenuAudienceMode mode) {
        MenuItem m = withPlans(item("t", null), TenantPlan.values());
        m.setSource(MenuSource.TEMPORARY);
        m.setLifecycleStatus(status);
        m.setStartsAt(java.time.Instant.parse(starts));
        m.setEndsAt(java.time.Instant.parse(ends));
        m.setAudienceMode(mode);
        return m;
    }

    private Subject at(java.time.Instant now, Set<UUID> granted) {
        return new Subject(Set.of(doctor.getId()), Set.of(), false, "CLINIC", "pro", Set.of(), Map.of(), Map.of(),
                Set.of(), granted, now);
    }

    @Test
    void temporaryMenuIsVisibleOnlyInsideItsWindowAndEndIsExclusive() {
        MenuItem m = temp(MenuLifecycle.ACTIVE, "2026-11-01T10:00:00Z", "2026-11-01T12:00:00Z", MenuAudienceMode.ALL_ELIGIBLE);
        assertThat(MenuVisibilityPolicy.evaluate(CatalogItem.of(m), at(T0.minusSeconds(1), Set.of()))).isEqualTo(Verdict.NOT_LIVE);
        assertThat(MenuVisibilityPolicy.evaluate(CatalogItem.of(m), at(T0, Set.of()))).isEqualTo(Verdict.VISIBLE);              // start inclusive
        assertThat(MenuVisibilityPolicy.evaluate(CatalogItem.of(m), at(T0.plusSeconds(7199), Set.of()))).isEqualTo(Verdict.VISIBLE);
        assertThat(MenuVisibilityPolicy.evaluate(CatalogItem.of(m), at(T0.plusSeconds(7200), Set.of()))).isEqualTo(Verdict.NOT_LIVE); // end exclusive
    }

    @Test
    void windowIsCheckedEvenWhenTheStoredStatusIsStale() {
        // the scheduler has not run: status still says ACTIVE long after the end
        MenuItem m = temp(MenuLifecycle.ACTIVE, "2026-11-01T10:00:00Z", "2026-11-01T12:00:00Z", MenuAudienceMode.ALL_ELIGIBLE);
        assertThat(MenuVisibilityPolicy.evaluate(CatalogItem.of(m), at(T0.plus(java.time.Duration.ofDays(30)), Set.of())))
                .isEqualTo(Verdict.NOT_LIVE);
        // and a SCHEDULED menu whose start has passed is already visible
        MenuItem s = temp(MenuLifecycle.SCHEDULED, "2026-11-01T10:00:00Z", "2026-11-01T12:00:00Z", MenuAudienceMode.ALL_ELIGIBLE);
        assertThat(MenuVisibilityPolicy.evaluate(CatalogItem.of(s), at(T0.plusSeconds(60), Set.of()))).isEqualTo(Verdict.VISIBLE);
    }

    @Test
    void cancelledDisabledAndExpiredStatusesAreNeverVisible() {
        for (MenuLifecycle st : new MenuLifecycle[]{MenuLifecycle.CANCELLED, MenuLifecycle.DISABLED, MenuLifecycle.EXPIRED, MenuLifecycle.DRAFT}) {
            MenuItem m = temp(st, "2026-11-01T10:00:00Z", "2026-11-01T12:00:00Z", MenuAudienceMode.ALL_ELIGIBLE);
            assertThat(MenuVisibilityPolicy.evaluate(CatalogItem.of(m), at(T0.plusSeconds(60), Set.of()))).isEqualTo(Verdict.NOT_LIVE);
        }
    }

    @Test
    void restrictedNeedsAGrantWhileAllEligibleDoesNot() {
        MenuItem restricted = temp(MenuLifecycle.ACTIVE, "2026-11-01T10:00:00Z", "2026-11-01T12:00:00Z", MenuAudienceMode.RESTRICTED);
        MenuItem open = temp(MenuLifecycle.ACTIVE, "2026-11-01T10:00:00Z", "2026-11-01T12:00:00Z", MenuAudienceMode.ALL_ELIGIBLE);
        assertThat(MenuVisibilityPolicy.evaluate(CatalogItem.of(restricted), at(T0, Set.of()))).isEqualTo(Verdict.NOT_GRANTED);
        assertThat(MenuVisibilityPolicy.evaluate(CatalogItem.of(restricted), at(T0, Set.of(restricted.getId())))).isEqualTo(Verdict.VISIBLE);
        assertThat(MenuVisibilityPolicy.evaluate(CatalogItem.of(open), at(T0, Set.of()))).isEqualTo(Verdict.VISIBLE);
    }

    @Test
    void allEligibleDoesNotBypassPlanOrPrivilege() {
        MenuItem m = temp(MenuLifecycle.ACTIVE, "2026-11-01T10:00:00Z", "2026-11-01T12:00:00Z", MenuAudienceMode.ALL_ELIGIBLE);
        m.setRequiredPrivilege(privilege("user.manage"));
        assertThat(MenuVisibilityPolicy.evaluate(CatalogItem.of(m), at(T0, Set.of()))).isEqualTo(Verdict.PRIVILEGE);
        MenuItem proOnly = temp(MenuLifecycle.ACTIVE, "2026-11-01T10:00:00Z", "2026-11-01T12:00:00Z", MenuAudienceMode.ALL_ELIGIBLE);
        proOnly.getPlanAccess().clear();
        MenuTestFixtures.withPlans(proOnly, TenantPlan.enterprise);
        assertThat(MenuVisibilityPolicy.evaluate(CatalogItem.of(proOnly), at(T0, Set.of()))).isEqualTo(Verdict.PLAN);
    }

    @Test
    void permanentItemsIgnoreTheWindowFields() {
        MenuItem m = open("perm", null, doctor);
        assertThat(m.isLiveAt(java.time.Instant.parse("2099-01-01T00:00:00Z"))).isTrue();
    }

    // ── module items ───────────────────────────────────────────────────────

    private MenuItem moduleItem(String privilegeCode) {
        MenuItem m = withPlans(item("billing.invoices", null), TenantPlan.values());   // no role grant at all
        m.setSource(MenuSource.MODULE);
        if (privilegeCode != null) m.setRequiredPrivilege(privilege(privilegeCode));
        return m;
    }

    @Test
    void moduleItemIsGatedByItsPrivilegeAloneNotByARoleGrant() {
        MenuItem m = moduleItem("invoice.view");
        assertThat(MenuVisibilityPolicy.evaluate(CatalogItem.of(m),
                subject(Map.of(), Map.of(), false, Set.of("invoice.view"), Set.of(), "pro"))).isEqualTo(Verdict.VISIBLE);
        assertThat(MenuVisibilityPolicy.evaluate(CatalogItem.of(m),
                subject(Map.of(), Map.of(), false, Set.of("other.view"), Set.of(), "pro"))).isEqualTo(Verdict.PRIVILEGE);
    }

    @Test
    void anAdministratorCanStillHideOrDenyAModuleItem() {
        MenuItem m = moduleItem("invoice.view");
        Subject denied = subject(Map.of(m.getId(), MenuAccessEffect.DENY), Map.of(), false, Set.of("invoice.view"), Set.of(), "pro");
        assertThat(MenuVisibilityPolicy.evaluate(CatalogItem.of(m), denied)).isEqualTo(Verdict.USER_DENIED);
        Subject hidden = subject(Map.of(), Map.of(m.getId(), new CatalogOverride(null, null, null, true)), false, Set.of("invoice.view"), Set.of(), "pro");
        assertThat(MenuVisibilityPolicy.evaluate(CatalogItem.of(m), hidden)).isEqualTo(Verdict.TENANT_HIDDEN);
    }

    @Test
    void aModuleItemStillRespectsPlanAndFeatureGates() {
        MenuItem m = moduleItem("invoice.view");
        m.setRequiredFeature("billing");
        assertThat(MenuVisibilityPolicy.evaluate(CatalogItem.of(m),
                subject(Map.of(), Map.of(), false, Set.of("invoice.view"), Set.of(), "pro"))).isEqualTo(Verdict.FEATURE);
    }

    @Test
    void suspendedOrDisabledModuleItemsAreNotLive() {
        for (MenuLifecycle st : new MenuLifecycle[]{MenuLifecycle.DISABLED, MenuLifecycle.DRAFT}) {
            MenuItem m = moduleItem("invoice.view");
            m.setLifecycleStatus(st);
            assertThat(MenuVisibilityPolicy.evaluate(CatalogItem.of(m),
                    subject(Map.of(), Map.of(), false, Set.of("invoice.view"), Set.of(), "pro"))).isEqualTo(Verdict.NOT_LIVE);
        }
    }

    @Test
    void aPlatformItemWithoutAGrantIsStillHidden() {
        // the module exemption must not leak to ordinary items
        MenuItem m = withPlans(item("plain", null), TenantPlan.values());
        assertThat(MenuVisibilityPolicy.evaluate(CatalogItem.of(m), plain())).isEqualTo(Verdict.NOT_GRANTED);
    }
}
