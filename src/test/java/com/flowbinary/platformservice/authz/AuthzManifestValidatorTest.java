package com.flowbinary.platformservice.authz;

import com.flowbinary.platformservice.authz.model.AuthzPrivilege;
import com.flowbinary.platformservice.authz.service.AuthzManifest;
import com.flowbinary.platformservice.authz.service.AuthzManifest.GrantDecl;
import com.flowbinary.platformservice.authz.service.AuthzManifest.PrivilegeDecl;
import com.flowbinary.platformservice.authz.service.AuthzManifestValidator;
import com.flowbinary.platformservice.authz.service.AuthzManifestValidator.Context;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AuthzManifestValidatorTest {

    private static final Context EMPTY = new Context(Map.of(), Set.of("ai_pack"), Set.of("LAB/lab_technician", "CLINIC/doctor"));

    private static PrivilegeDecl priv(String code) {
        return new PrivilegeDecl(code, "Name", "desc", List.of("LAB"), null, null, false, false, null);
    }

    private static AuthzManifest manifest(PrivilegeDecl... p) {
        return new AuthzManifest("imaging", "1", List.of(p), List.of());
    }

    private static List<String> check(AuthzManifest m) {
        return AuthzManifestValidator.validate("imaging", m, EMPTY);
    }

    private static List<String> check(AuthzManifest m, Context ctx) {
        return AuthzManifestValidator.validate("imaging", m, ctx);
    }

    private static AuthzPrivilege existing(String code, boolean phi, boolean write, String risk, String... types) {
        AuthzPrivilege p = new AuthzPrivilege();
        p.setCode(code);
        p.setOwnerService("imaging");
        p.setPhi(phi);
        p.setClinicalWrite(write);
        p.setRisk(risk);
        p.setTenantTypes(List.of(types));
        p.setStatus("ACTIVE");
        return p;
    }

    @Test
    void aWellFormedManifestHasNoProblems() {
        assertThat(check(manifest(priv("imaging.study.view"), priv("imaging.report.sign")))).isEmpty();
    }

    @Test
    void theServiceMustMatchThePathAndPlatformIsReserved() {
        assertThat(AuthzManifestValidator.validate("imaging", new AuthzManifest("other", "1", List.of(priv("imaging.a.b")), List.of()), EMPTY))
                .anyMatch(e -> e.contains("must be 'imaging'"));
        assertThat(AuthzManifestValidator.validate("platform", new AuthzManifest("platform", "1", List.of(priv("platform.a.b")), List.of()), EMPTY))
                .anyMatch(e -> e.contains("reserved"));
        assertThat(AuthzManifestValidator.validate("X!", new AuthzManifest("X!", "1", List.of(priv("x.a.b")), List.of()), EMPTY))
                .anyMatch(e -> e.contains("not a valid service name"));
    }

    @Test
    void codesStayInTheServicesNamespaceAndHaveThreeParts() {
        assertThat(check(manifest(priv("lab.result.release")))).anyMatch(e -> e.contains("outside the 'imaging' namespace"));
        assertThat(check(manifest(priv("imaging.view")))).anyMatch(e -> e.contains("<service>.<resource>.<action>"));
        assertThat(check(manifest(priv("Imaging.Study.View")))).anyMatch(e -> e.contains("<service>.<resource>.<action>"));
        assertThat(check(manifest(priv("imaging.study.view"), priv("imaging.study.view")))).anyMatch(e -> e.contains("declared twice"));
    }

    @Test
    void aManifestMustDeclareAtLeastOnePrivilege() {
        assertThat(check(new AuthzManifest("imaging", "1", List.of(), List.of()))).anyMatch(e -> e.contains("at least one"));
        assertThat(check(new AuthzManifest("imaging", "1", null, null))).anyMatch(e -> e.contains("at least one"));
    }

    @Test
    void namesTenantTypesScopeRiskAndFeatureAreChecked() {
        PrivilegeDecl bad = new PrivilegeDecl("imaging.study.view", " ", "d", List.of("HOSPITAL"), "ghost", "WORLD", null, null, "EXTREME");
        List<String> errors = check(manifest(bad));
        assertThat(errors).anyMatch(e -> e.contains(".name is required"))
                .anyMatch(e -> e.contains("unknown tenant type 'HOSPITAL'"))
                .anyMatch(e -> e.contains(".scope must be"))
                .anyMatch(e -> e.contains(".risk must be"))
                .anyMatch(e -> e.contains("feature 'ghost' does not exist"));
        assertThat(check(manifest(new PrivilegeDecl("imaging.study.view", "N", null, List.of(), null, null, null, null, null))))
                .anyMatch(e -> e.contains("at least one tenant type"));
        assertThat(check(manifest(new PrivilegeDecl("imaging.study.view", "N", null, List.of("LAB"), "ai_pack", "SELF", null, null, "HIGH")))).isEmpty();
    }

    @Test
    void everyProblemIsReportedNotJustTheFirst() {
        List<String> errors = check(manifest(priv("lab.x.y"), priv("bad"), new PrivilegeDecl("imaging.a.b", "", null, List.of(), null, null, null, null, null)));
        assertThat(errors.size()).isGreaterThanOrEqualTo(4);
    }

    // ── loosening ──────────────────────────────────────────────────────────

    @Test
    void safetyFlagsAndReachCanTightenButNeverLoosen() {
        Context ctx = new Context(Map.of("imaging.study.view", existing("imaging.study.view", true, true, "HIGH", "LAB", "CLINIC")), Set.of(), Set.of());

        PrivilegeDecl loosened = new PrivilegeDecl("imaging.study.view", "N", null, List.of("LAB"), null, null, false, false, "LOW");
        assertThat(check(manifest(loosened), ctx))
                .anyMatch(e -> e.contains("phi cannot be lowered"))
                .anyMatch(e -> e.contains("clinicalWrite cannot be lowered"))
                .anyMatch(e -> e.contains("risk cannot be lowered from HIGH"))
                .anyMatch(e -> e.contains("cannot drop CLINIC"));

        PrivilegeDecl tightened = new PrivilegeDecl("imaging.study.view", "N", null, List.of("LAB", "CLINIC", "STORE"), null, null, true, true, "CRITICAL");
        assertThat(check(manifest(tightened), ctx)).isEmpty();
    }

    // ── template grants ────────────────────────────────────────────────────

    private static AuthzManifest withGrant(GrantDecl g, PrivilegeDecl... p) {
        return new AuthzManifest("imaging", "1", List.of(p), List.of(g));
    }

    @Test
    void aGrantNeedsARealTemplateAndTheServicesOwnPrivilegeThatExistsForThatTenantType() {
        PrivilegeDecl labOnly = priv("imaging.study.view");
        assertThat(check(withGrant(new GrantDecl("LAB", "lab_technician", List.of("imaging.study.view")), labOnly))).isEmpty();
        assertThat(check(withGrant(new GrantDecl("LAB", "ghost", List.of("imaging.study.view")), labOnly)))
                .anyMatch(e -> e.contains("no template 'ghost'"));
        assertThat(check(withGrant(new GrantDecl("CLINIC", "doctor", List.of("imaging.study.view")), labOnly)))
                .anyMatch(e -> e.contains("does not exist for CLINIC"));
        assertThat(check(withGrant(new GrantDecl("LAB", "lab_technician", List.of("lab.result.release")), labOnly)))
                .anyMatch(e -> e.contains("only propose its own privileges"));
    }
}
