package com.flowbinary.platformservice.authz;

import com.flowbinary.platformservice.authz.model.AuthzPrivilege;
import com.flowbinary.platformservice.authz.model.DeltaEffect;
import com.flowbinary.platformservice.authz.service.PermissionCalculator;
import com.flowbinary.platformservice.authz.service.PermissionCalculator.RoleContribution;
import com.flowbinary.platformservice.authz.service.PermissionCalculator.Scope;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.flowbinary.platformservice.authz.model.DeltaEffect.DENY;
import static com.flowbinary.platformservice.authz.model.DeltaEffect.GRANT;
import static org.assertj.core.api.Assertions.assertThat;

class PermissionCalculatorTest {

    private static final Map<String, AuthzPrivilege> CATALOGUE = Map.of(
            "clinical.encounter.sign", tenantPriv("clinical.encounter.sign", null, "CLINIC"),
            "clinical.vitals.record", tenantPriv("clinical.vitals.record", null, "CLINIC"),
            "patient.record.view", tenantPriv("patient.record.view", null, "CLINIC", "LAB", "STORE"),
            "billing.invoice.view", tenantPriv("billing.invoice.view", null, "CLINIC", "LAB", "STORE"),
            "lab.result.enter", tenantPriv("lab.result.enter", null, "LAB"),
            "ai.assistant.use", tenantPriv("ai.assistant.use", "ai_pack", "CLINIC"),
            "platform.tenant.view", platformPriv("platform.tenant.view"));

    private static final Scope CLINIC = new Scope("CLINIC", Set.of());

    @Test
    void unionOfTemplateAndGrants() {
        var doctor = role(Set.of("clinical.encounter.sign", "patient.record.view"), Map.of());
        var extra = role(Set.of(), Map.of("billing.invoice.view", GRANT));
        assertThat(eff(CLINIC, doctor, extra))
                .containsExactlyInAnyOrder("clinical.encounter.sign", "patient.record.view", "billing.invoice.view");
    }

    @Test
    void denyOnTheSameRoleRemovesATemplatePrivilege() {
        var nurseWithoutVitals = role(Set.of("clinical.vitals.record", "patient.record.view"),
                Map.of("clinical.vitals.record", DENY));
        assertThat(eff(CLINIC, nurseWithoutVitals)).containsExactly("patient.record.view");
    }

    @Test
    void denyOnAnExtraRoleBeatsTheGrantOfThePrimary() {
        var doctor = role(Set.of("clinical.encounter.sign", "patient.record.view"), Map.of());
        var noSigning = role(Set.of(), Map.of("clinical.encounter.sign", DENY));
        assertThat(eff(CLINIC, doctor, noSigning)).containsExactly("patient.record.view");
    }

    @Test
    void denyBeatsAnExplicitGrantOnAnotherRole() {
        var grants = role(Set.of(), Map.of("billing.invoice.view", GRANT));
        var denies = role(Set.of(), Map.of("billing.invoice.view", DENY));
        assertThat(eff(CLINIC, grants, denies)).isEmpty();
    }

    @Test
    void privilegesOfAnotherTenantTypeAreDropped() {
        var r = role(Set.of("lab.result.enter", "patient.record.view"), Map.of());
        assertThat(eff(CLINIC, r)).containsExactly("patient.record.view");
        assertThat(eff(new Scope("LAB", Set.of()), r)).containsExactlyInAnyOrder("lab.result.enter", "patient.record.view");
    }

    @Test
    void privilegesNeedTheirPlanFeature() {
        var r = role(Set.of("ai.assistant.use"), Map.of());
        assertThat(eff(CLINIC, r)).isEmpty();
        assertThat(eff(new Scope("CLINIC", Set.of("ai_pack")), r)).containsExactly("ai.assistant.use");
    }

    @Test
    void tenantScopeNeverYieldsPlatformPrivilegesAndViceVersa() {
        var r = role(Set.of(), Map.of("platform.tenant.view", GRANT, "patient.record.view", GRANT));
        assertThat(eff(CLINIC, r)).containsExactly("patient.record.view");
        assertThat(eff(Scope.platform(), r)).containsExactly("platform.tenant.view");
    }

    @Test
    void unknownCodesAreIgnored() {
        assertThat(eff(CLINIC, role(Set.of("no.such.thing"), Map.of()))).isEmpty();
    }

    @Test
    void noRolesNoPrivileges() {
        assertThat(PermissionCalculator.effective(List.of(), CATALOGUE, CLINIC)).isEmpty();
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private static Set<String> eff(Scope scope, RoleContribution... roles) {
        return PermissionCalculator.effective(List.of(roles), CATALOGUE, scope);
    }

    private static RoleContribution role(Set<String> template, Map<String, DeltaEffect> deltas) {
        return new RoleContribution(template, deltas);
    }

    private static AuthzPrivilege tenantPriv(String code, String feature, String... types) {
        AuthzPrivilege p = base(code);
        p.setTenantTypes(List.of(types));
        p.setAssignableByTenant(true);
        p.setFeatureCode(feature);
        return p;
    }

    private static AuthzPrivilege platformPriv(String code) {
        AuthzPrivilege p = base(code);
        p.setTenantTypes(List.of());
        p.setAssignableByTenant(false);
        return p;
    }

    private static AuthzPrivilege base(String code) {
        AuthzPrivilege p = new AuthzPrivilege();
        p.setCode(code);
        p.setOwnerService(code.substring(0, code.indexOf('.')));
        p.setStatus("ACTIVE");
        p.setRisk("LOW");
        p.setScopeKind("TENANT");
        return p;
    }
}
