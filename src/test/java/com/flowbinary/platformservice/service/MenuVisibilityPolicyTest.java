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

    private Subject subject(Map<UUID, MenuAccessEffect> user, Map<UUID, MenuTenantOverride> tenant,
                            boolean superadmin, Set<String> privileges, Set<String> features, String plan) {
        return new Subject(Set.of(doctor.getId()), privileges, superadmin, plan, features, user, tenant);
    }

    private Subject plain() {
        return subject(Map.of(), Map.of(), false, Set.of(), Set.of(), "pro");
    }

    @Test
    void roleGrantMakesItemVisible() {
        MenuItem m = open("a", null, doctor);
        assertThat(MenuVisibilityPolicy.evaluate(m, plain())).isEqualTo(Verdict.VISIBLE);
    }

    @Test
    void noGrantMeansHidden() {
        MenuItem m = withPlans(item("a", null), TenantPlan.values());
        assertThat(MenuVisibilityPolicy.evaluate(m, plain())).isEqualTo(Verdict.NOT_GRANTED);
    }

    @Test
    void userDenyBeatsRoleGrant() {
        MenuItem m = open("a", null, doctor);
        Subject s = subject(Map.of(m.getId(), MenuAccessEffect.DENY), Map.of(), false, Set.of(), Set.of(), "pro");
        assertThat(MenuVisibilityPolicy.evaluate(m, s)).isEqualTo(Verdict.USER_DENIED);
    }

    @Test
    void userGrantAddsItemWithoutRole() {
        MenuItem m = withPlans(item("a", null), TenantPlan.values());
        Subject s = subject(Map.of(m.getId(), MenuAccessEffect.GRANT), Map.of(), false, Set.of(), Set.of(), "pro");
        assertThat(MenuVisibilityPolicy.evaluate(m, s)).isEqualTo(Verdict.VISIBLE);
    }

    @Test
    void userGrantCannotBypassPlan() {
        MenuItem m = withPlans(item("a", null), TenantPlan.enterprise);
        Subject s = subject(Map.of(m.getId(), MenuAccessEffect.GRANT), Map.of(), false, Set.of(), Set.of(), "free");
        assertThat(MenuVisibilityPolicy.evaluate(m, s)).isEqualTo(Verdict.PLAN);
    }

    @Test
    void userGrantCannotBypassFeature() {
        MenuItem m = open("a", null, doctor);
        m.setRequiredFeature("ai");
        Subject s = subject(Map.of(m.getId(), MenuAccessEffect.GRANT), Map.of(), false, Set.of(), Set.of(), "pro");
        assertThat(MenuVisibilityPolicy.evaluate(m, s)).isEqualTo(Verdict.FEATURE);
    }

    @Test
    void userGrantCannotBypassPrivilege() {
        MenuItem m = withPlans(item("a", null), TenantPlan.values());
        m.setRequiredPrivilege(privilege("user.manage"));
        Subject s = subject(Map.of(m.getId(), MenuAccessEffect.GRANT), Map.of(), false, Set.of(), Set.of(), "pro");
        assertThat(MenuVisibilityPolicy.evaluate(m, s)).isEqualTo(Verdict.PRIVILEGE);
    }

    @Test
    void superadminBypassesPrivilegeAndGrantButNotPlanOrDeny() {
        MenuItem m = withPlans(item("a", null), TenantPlan.pro);
        m.setRequiredPrivilege(privilege("user.manage"));
        assertThat(MenuVisibilityPolicy.evaluate(m, subject(Map.of(), Map.of(), true, Set.of(), Set.of(), "pro")))
                .isEqualTo(Verdict.VISIBLE);
        assertThat(MenuVisibilityPolicy.evaluate(m, subject(Map.of(), Map.of(), true, Set.of(), Set.of(), "free")))
                .isEqualTo(Verdict.PLAN);
        assertThat(MenuVisibilityPolicy.evaluate(m,
                subject(Map.of(m.getId(), MenuAccessEffect.DENY), Map.of(), true, Set.of(), Set.of(), "pro")))
                .isEqualTo(Verdict.USER_DENIED);
    }

    @Test
    void tenantHideBeatsUserGrant() {
        MenuItem m = withPlans(item("a", null), TenantPlan.values());
        MenuTenantOverride hide = MenuTenantOverride.builder().menuItem(m).isHidden(true).build();
        Subject s = subject(Map.of(m.getId(), MenuAccessEffect.GRANT), Map.of(m.getId(), hide),
                false, Set.of(), Set.of(), "pro");
        assertThat(MenuVisibilityPolicy.evaluate(m, s)).isEqualTo(Verdict.TENANT_HIDDEN);
    }

    @Test
    void notInNavigationIsHidden() {
        MenuItem m = open("a", null, doctor);
        m.setIsVisible(false);
        assertThat(MenuVisibilityPolicy.evaluate(m, plain())).isEqualTo(Verdict.NOT_IN_NAVIGATION);
    }
}
