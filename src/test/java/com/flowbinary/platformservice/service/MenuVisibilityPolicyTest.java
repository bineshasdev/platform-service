package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.model.*;
import com.flowbinary.platformservice.service.MenuVisibilityPolicy.Subject;
import com.flowbinary.platformservice.service.MenuVisibilityPolicy.Verdict;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.flowbinary.platformservice.service.MenuTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Navigation follows privileges: every verdict of {@link MenuVisibilityPolicy}, in its fixed order. */
class MenuVisibilityPolicyTest {

    private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");

    private Subject subject(Map<UUID, MenuAccessEffect> user, Map<UUID, CatalogOverride> tenant,
                            boolean superadmin, Set<String> privileges, Set<String> features, String plan) {
        return new Subject(privileges, superadmin, "CLINIC", plan, features, user, tenant, NOW);
    }

    private Subject plain() {
        return subject(Map.of(), Map.of(), false, Set.of(), Set.of(), "pro");
    }

    private Subject holding(String... privileges) {
        return subject(Map.of(), Map.of(), false, Set.of(privileges), Set.of(), "pro");
    }

    private Subject ofType(String tenantType, boolean superadmin, Map<UUID, MenuAccessEffect> user) {
        return new Subject(Set.of(), superadmin, tenantType, "pro", Set.of(), user, Map.of(), NOW);
    }

    private static Verdict verdict(MenuItem m, Subject s) {
        return MenuVisibilityPolicy.evaluate(CatalogItem.of(m), s);
    }

    // ── Ungated and privilege-gated items ───────────────────────────────────

    @Test
    void anItemWithNoRequiredPrivilegeIsVisibleToEveryMember() {
        assertThat(verdict(open("a", null), plain())).isEqualTo(Verdict.VISIBLE);
    }

    @Test
    void aGatedItemShowsOnlyToHoldersOfThePrivilege() {
        MenuItem m = open("a", null);
        m.setRequiredPrivilege("platform.member.manage");
        assertThat(verdict(m, holding("platform.member.manage", "other.thing.view"))).isEqualTo(Verdict.VISIBLE);
        assertThat(verdict(m, holding("other.thing.view"))).isEqualTo(Verdict.PRIVILEGE);
        assertThat(verdict(m, plain())).isEqualTo(Verdict.PRIVILEGE);
    }

    // ── Mandatory gates ─────────────────────────────────────────────────────

    @Test
    void itemWithoutTenantTypesIsShownToEveryType() {
        MenuItem m = open("a", null);
        assertThat(verdict(m, ofType("LAB", false, Map.of()))).isEqualTo(Verdict.VISIBLE);
        assertThat(verdict(m, ofType(null, false, Map.of()))).isEqualTo(Verdict.VISIBLE);
    }

    @Test
    void tenantTypeGateHidesItemFromOtherTypes() {
        MenuItem m = open("charting", null);
        m.setTenantTypes(List.of("CLINIC", "STORE"));
        assertThat(verdict(m, ofType("STORE", false, Map.of()))).isEqualTo(Verdict.VISIBLE);
        assertThat(verdict(m, ofType("LAB", false, Map.of()))).isEqualTo(Verdict.TENANT_TYPE);
    }

    @Test
    void unknownTenantTypePassesOnlyUngatedItems() {
        MenuItem m = open("a", null);
        m.setTenantTypes(List.of("CLINIC"));
        assertThat(verdict(m, ofType(null, false, Map.of()))).isEqualTo(Verdict.TENANT_TYPE);
    }

    @Test
    void tenantTypeIsMandatoryEvenForSuperadminAndUserGrant() {
        MenuItem m = open("dispense", null);
        m.setTenantTypes(List.of("STORE"));
        assertThat(verdict(m, ofType("CLINIC", true, Map.of()))).isEqualTo(Verdict.TENANT_TYPE);
        assertThat(verdict(m, ofType("CLINIC", false, Map.of(m.getId(), MenuAccessEffect.GRANT)))).isEqualTo(Verdict.TENANT_TYPE);
    }

    @Test
    void userGrantCannotBypassPlanFeatureOrPrivilege() {
        MenuItem plan = withPlans(item("a", null), TenantPlan.enterprise);
        assertThat(verdict(plan, subject(Map.of(plan.getId(), MenuAccessEffect.GRANT), Map.of(), false, Set.of(), Set.of(), "free")))
                .isEqualTo(Verdict.PLAN);

        MenuItem feature = open("b", null);
        feature.setRequiredFeature("ai");
        assertThat(verdict(feature, subject(Map.of(feature.getId(), MenuAccessEffect.GRANT), Map.of(), false, Set.of(), Set.of(), "pro")))
                .isEqualTo(Verdict.FEATURE);

        MenuItem gated = open("c", null);
        gated.setRequiredPrivilege("platform.member.manage");
        assertThat(verdict(gated, subject(Map.of(gated.getId(), MenuAccessEffect.GRANT), Map.of(), false, Set.of(), Set.of(), "pro")))
                .isEqualTo(Verdict.PRIVILEGE);
    }

    @Test
    void superadminBypassesPrivilegeButNotPlanOrDeny() {
        MenuItem m = withPlans(item("a", null), TenantPlan.pro);
        m.setRequiredPrivilege("platform.member.manage");
        assertThat(verdict(m, subject(Map.of(), Map.of(), true, Set.of(), Set.of(), "pro"))).isEqualTo(Verdict.VISIBLE);
        assertThat(verdict(m, subject(Map.of(), Map.of(), true, Set.of(), Set.of(), "free"))).isEqualTo(Verdict.PLAN);
        assertThat(verdict(m, subject(Map.of(m.getId(), MenuAccessEffect.DENY), Map.of(), true, Set.of(), Set.of(), "pro")))
                .isEqualTo(Verdict.USER_DENIED);
    }

    @Test
    void notInNavigationIsHidden() {
        MenuItem m = open("a", null);
        m.setIsVisible(false);
        assertThat(verdict(m, plain())).isEqualTo(Verdict.NOT_IN_NAVIGATION);
    }

    // ── User and tenant overrides ───────────────────────────────────────────

    @Test
    void userDenyHidesAnItemThePrivilegeAllows() {
        MenuItem m = open("a", null);
        m.setRequiredPrivilege("platform.member.manage");
        Subject s = subject(Map.of(m.getId(), MenuAccessEffect.DENY), Map.of(), false, Set.of("platform.member.manage"), Set.of(), "pro");
        assertThat(verdict(m, s)).isEqualTo(Verdict.USER_DENIED);
    }

    @Test
    void tenantHideHidesTheItemForEveryone() {
        MenuItem m = open("a", null);
        CatalogOverride hide = new CatalogOverride(null, null, null, true);
        assertThat(verdict(m, subject(Map.of(), Map.of(m.getId(), hide), false, Set.of(), Set.of(), "pro")))
                .isEqualTo(Verdict.TENANT_HIDDEN);
    }

    @Test
    void userGrantShowsAnItemTheTenantHid() {
        MenuItem m = open("a", null);
        CatalogOverride hide = new CatalogOverride(null, null, null, true);
        Subject s = subject(Map.of(m.getId(), MenuAccessEffect.GRANT), Map.of(m.getId(), hide), false, Set.of(), Set.of(), "pro");
        assertThat(verdict(m, s)).isEqualTo(Verdict.VISIBLE);
    }

    @Test
    void userDenyBeatsUserGrantStyleAndTenantHideTogether() {
        MenuItem m = open("a", null);
        CatalogOverride hide = new CatalogOverride(null, null, null, true);
        Subject s = subject(Map.of(m.getId(), MenuAccessEffect.DENY), Map.of(m.getId(), hide), false, Set.of(), Set.of(), "pro");
        assertThat(verdict(m, s)).isEqualTo(Verdict.USER_DENIED);
    }

    // ── Temporary menus ─────────────────────────────────────────────────────

    private MenuItem temp(MenuLifecycle status, String starts, String ends, MenuAudienceMode mode) {
        MenuItem m = withPlans(item("t", null), TenantPlan.values());
        m.setSource(MenuSource.TEMPORARY);
        m.setLifecycleStatus(status);
        m.setStartsAt(Instant.parse(starts));
        m.setEndsAt(Instant.parse(ends));
        m.setAudienceMode(mode);
        return m;
    }

    private Subject at(Instant now, Map<UUID, MenuAccessEffect> user) {
        return new Subject(Set.of(), false, "CLINIC", "pro", Set.of(), user, Map.of(), now);
    }

    @Test
    void temporaryMenuIsVisibleOnlyInsideItsWindowAndEndIsExclusive() {
        MenuItem m = temp(MenuLifecycle.ACTIVE, "2026-11-01T10:00:00Z", "2026-11-01T12:00:00Z", MenuAudienceMode.ALL_ELIGIBLE);
        assertThat(verdict(m, at(NOW.minusSeconds(1), Map.of()))).isEqualTo(Verdict.NOT_LIVE);
        assertThat(verdict(m, at(NOW, Map.of()))).isEqualTo(Verdict.VISIBLE);                       // start inclusive
        assertThat(verdict(m, at(NOW.plusSeconds(7199), Map.of()))).isEqualTo(Verdict.VISIBLE);
        assertThat(verdict(m, at(NOW.plusSeconds(7200), Map.of()))).isEqualTo(Verdict.NOT_LIVE);    // end exclusive
    }

    @Test
    void windowIsCheckedEvenWhenTheStoredStatusIsStale() {
        // the scheduler has not run: status still says ACTIVE long after the end
        MenuItem m = temp(MenuLifecycle.ACTIVE, "2026-11-01T10:00:00Z", "2026-11-01T12:00:00Z", MenuAudienceMode.ALL_ELIGIBLE);
        assertThat(verdict(m, at(NOW.plus(Duration.ofDays(30)), Map.of()))).isEqualTo(Verdict.NOT_LIVE);
        // and a SCHEDULED menu whose start has passed is already visible
        MenuItem s = temp(MenuLifecycle.SCHEDULED, "2026-11-01T10:00:00Z", "2026-11-01T12:00:00Z", MenuAudienceMode.ALL_ELIGIBLE);
        assertThat(verdict(s, at(NOW.plusSeconds(60), Map.of()))).isEqualTo(Verdict.VISIBLE);
    }

    @Test
    void cancelledDisabledAndExpiredStatusesAreNeverVisible() {
        for (MenuLifecycle st : new MenuLifecycle[]{MenuLifecycle.CANCELLED, MenuLifecycle.DISABLED, MenuLifecycle.EXPIRED, MenuLifecycle.DRAFT}) {
            MenuItem m = temp(st, "2026-11-01T10:00:00Z", "2026-11-01T12:00:00Z", MenuAudienceMode.ALL_ELIGIBLE);
            assertThat(verdict(m, at(NOW.plusSeconds(60), Map.of()))).isEqualTo(Verdict.NOT_LIVE);
        }
    }

    @Test
    void aRestrictedMenuNeedsAUserGrantWhileAllEligibleDoesNot() {
        MenuItem restricted = temp(MenuLifecycle.ACTIVE, "2026-11-01T10:00:00Z", "2026-11-01T12:00:00Z", MenuAudienceMode.RESTRICTED);
        MenuItem open = temp(MenuLifecycle.ACTIVE, "2026-11-01T10:00:00Z", "2026-11-01T12:00:00Z", MenuAudienceMode.ALL_ELIGIBLE);
        assertThat(verdict(restricted, at(NOW, Map.of()))).isEqualTo(Verdict.NOT_AUDIENCE);
        assertThat(verdict(restricted, at(NOW, Map.of(restricted.getId(), MenuAccessEffect.GRANT)))).isEqualTo(Verdict.VISIBLE);
        assertThat(verdict(restricted, at(NOW, Map.of(restricted.getId(), MenuAccessEffect.DENY)))).isEqualTo(Verdict.NOT_AUDIENCE);
        assertThat(verdict(open, at(NOW, Map.of()))).isEqualTo(Verdict.VISIBLE);
    }

    @Test
    void allEligibleDoesNotBypassPlanOrPrivilege() {
        MenuItem m = temp(MenuLifecycle.ACTIVE, "2026-11-01T10:00:00Z", "2026-11-01T12:00:00Z", MenuAudienceMode.ALL_ELIGIBLE);
        m.setRequiredPrivilege("platform.member.manage");
        assertThat(verdict(m, at(NOW, Map.of()))).isEqualTo(Verdict.PRIVILEGE);
        MenuItem proOnly = temp(MenuLifecycle.ACTIVE, "2026-11-01T10:00:00Z", "2026-11-01T12:00:00Z", MenuAudienceMode.ALL_ELIGIBLE);
        proOnly.getPlanAccess().clear();
        withPlans(proOnly, TenantPlan.enterprise);
        assertThat(verdict(proOnly, at(NOW, Map.of()))).isEqualTo(Verdict.PLAN);
    }

    @Test
    void permanentItemsIgnoreTheWindowFields() {
        MenuItem m = open("perm", null);
        assertThat(m.isLiveAt(Instant.parse("2099-01-01T00:00:00Z"))).isTrue();
    }

    // ── Module items ────────────────────────────────────────────────────────

    private MenuItem moduleItem(String privilegeCode) {
        MenuItem m = withPlans(item("billing.invoices", null), TenantPlan.values());
        m.setSource(MenuSource.MODULE);
        if (privilegeCode != null) m.setRequiredPrivilege(privilegeCode);
        return m;
    }

    @Test
    void moduleItemIsGatedByItsPrivilege() {
        MenuItem m = moduleItem("billing.invoice.view");
        assertThat(verdict(m, holding("billing.invoice.view"))).isEqualTo(Verdict.VISIBLE);
        assertThat(verdict(m, holding("other.view"))).isEqualTo(Verdict.PRIVILEGE);
    }

    @Test
    void anAdministratorCanStillHideOrDenyAModuleItem() {
        MenuItem m = moduleItem("billing.invoice.view");
        Subject denied = subject(Map.of(m.getId(), MenuAccessEffect.DENY), Map.of(), false, Set.of("billing.invoice.view"), Set.of(), "pro");
        assertThat(verdict(m, denied)).isEqualTo(Verdict.USER_DENIED);
        Subject hidden = subject(Map.of(), Map.of(m.getId(), new CatalogOverride(null, null, null, true)), false, Set.of("billing.invoice.view"), Set.of(), "pro");
        assertThat(verdict(m, hidden)).isEqualTo(Verdict.TENANT_HIDDEN);
    }

    @Test
    void aModuleItemStillRespectsPlanAndFeatureGates() {
        MenuItem m = moduleItem("billing.invoice.view");
        m.setRequiredFeature("billing");
        assertThat(verdict(m, holding("billing.invoice.view"))).isEqualTo(Verdict.FEATURE);
    }

    @Test
    void suspendedOrDisabledModuleItemsAreNotLive() {
        for (MenuLifecycle st : new MenuLifecycle[]{MenuLifecycle.DISABLED, MenuLifecycle.DRAFT}) {
            MenuItem m = moduleItem("billing.invoice.view");
            m.setLifecycleStatus(st);
            assertThat(verdict(m, holding("billing.invoice.view"))).isEqualTo(Verdict.NOT_LIVE);
        }
    }
}
