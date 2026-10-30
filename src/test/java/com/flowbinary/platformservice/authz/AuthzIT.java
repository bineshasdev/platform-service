package com.flowbinary.platformservice.authz;

import com.flowbinary.platformservice.authz.model.AuthzRole;
import com.flowbinary.platformservice.authz.model.DeltaEffect;
import com.flowbinary.platformservice.authz.model.Membership;
import com.flowbinary.platformservice.authz.model.MembershipStatus;
import com.flowbinary.platformservice.authz.sdk.Authz;
import com.flowbinary.platformservice.authz.sdk.AuthzSdkConfig;
import com.flowbinary.platformservice.authz.sdk.Decision;
import com.flowbinary.platformservice.authz.sdk.PermissionCache;
import com.flowbinary.platformservice.authz.service.*;
import com.flowbinary.platformservice.authz.service.MembershipService.AssignmentChange;
import com.flowbinary.platformservice.authz.service.MembershipService.Extra;
import com.flowbinary.platformservice.authz.service.RoleAdminService.CreateRole;
import com.flowbinary.platformservice.authz.service.RoleAdminService.UpdateRole;
import com.flowbinary.platformservice.config.AppProperties;
import com.flowbinary.platformservice.config.JpaConfig;
import com.flowbinary.platformservice.exception.AuthzRuleException;
import com.flowbinary.platformservice.exception.AuthzRuleException.Rule;
import com.flowbinary.platformservice.model.PlatformRole;
import com.flowbinary.platformservice.model.Tenant;
import com.flowbinary.platformservice.repository.TenantRepository;
import com.flowbinary.platformservice.security.TenantContextResolver;
import com.flowbinary.platformservice.service.AuditService;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The authorization context against a real PostgreSQL schema built by Flyway:
 * V31 seed, provisioning, effective permissions, every guardrail, the decision
 * bean and cache invalidation.
 *
 * <p>Opt-in. Point it at a scratch database it may migrate and fill:
 * <pre>AUTHZ_IT_DB_URL=jdbc:postgresql://localhost:5432/authz_it_scratch  [AUTHZ_IT_DB_USER=..  AUTHZ_IT_DB_PASSWORD=..]</pre>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "AUTHZ_IT_DB_URL", matches = ".+")
@Transactional(propagation = Propagation.NOT_SUPPORTED)   // the services manage their own transactions
@Import({JpaConfig.class, AppProperties.class, AuditService.class, TenantContextResolver.class, AuthzSdkConfig.class,
        EffectivePermissionService.class, TenantFeatures.class, PermissionCache.class, AuthzEvents.class,
        TenantRoleProvisioner.class, MembershipService.class, AuthzGuardrails.class, RoleAdminService.class,
        LegacyRoleBridge.class, Authz.class})
class AuthzIT {

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> System.getenv("AUTHZ_IT_DB_URL"));
        r.add("spring.datasource.username", () -> Optional.ofNullable(System.getenv("AUTHZ_IT_DB_USER")).orElse(System.getProperty("user.name")));
        r.add("spring.datasource.password", () -> Optional.ofNullable(System.getenv("AUTHZ_IT_DB_PASSWORD")).orElse(""));
        r.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired TenantRepository tenants;
    @Autowired EffectivePermissionService permissions;
    @Autowired LegacyRoleBridge bridge;
    @Autowired MembershipService memberships;
    @Autowired RoleAdminService roleAdmin;
    @Autowired TenantRoleProvisioner provisioner;
    @Autowired Authz authz;

    // ── seed ─────────────────────────────────────────────────────────────

    @Test
    void seedHasTemplatesForEveryTypeWithOneAdminAndOneFallback() {
        for (String type : List.of("CLINIC", "LAB", "STORE")) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM authz_role_templates WHERE tenant_type = ? AND is_admin", Integer.class, type)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM authz_role_templates WHERE tenant_type = ? AND is_fallback", Integer.class, type)).isEqualTo(1);
        }
        // Every admin template can administer.
        List<String> admins = jdbc.queryForList(
                "SELECT t.code FROM authz_role_templates t WHERE t.is_admin AND (SELECT count(*) FROM authz_role_template_privileges p " +
                "WHERE p.template_id = t.id AND p.privilege_code IN ('platform.member.manage','platform.role.manage')) = 2", String.class);
        assertThat(admins).containsExactlyInAnyOrder("clinic_admin", "lab_admin", "store_admin");
        // Every old privilege maps to a new one, except the retired group.manage.
        assertThat(jdbc.queryForList("SELECT code FROM privileges WHERE code NOT IN (SELECT legacy_code FROM authz_privileges WHERE legacy_code IS NOT NULL)", String.class))
                .containsExactly("group.manage");
    }

    // ── effective permissions ────────────────────────────────────────────

    @Test
    void tenantCreatorAdministersAndTypeDecidesTheTemplate() {
        Fx clinic = tenantWithAdmin("CLINIC");
        Fx lab = tenantWithAdmin("LAB");
        PermissionSet c = permissions.forTenant(clinic.tenant.getId(), clinic.admin);
        PermissionSet l = permissions.forTenant(lab.tenant.getId(), lab.admin);

        assertThat(c.primaryRole()).isEqualTo("clinic_admin");
        assertThat(l.primaryRole()).isEqualTo("lab_admin");
        assertThat(c.privileges()).containsAll(AuthzGuardrails.ADMIN_PRIVILEGES);
        assertThat(l.privileges()).contains("lab.catalog.manage").doesNotContain("clinical.encounter.view");
        assertThat(c.privileges()).doesNotContain("lab.catalog.manage");
    }

    @Test
    void sameUserHasDifferentRolesInDifferentTenants() {
        Fx clinic = tenantWithAdmin("CLINIC");
        Fx lab = tenantWithAdmin("LAB");
        UUID person = UUID.randomUUID();
        bridge.onUserCreated(clinic.tenant, person, PlatformRole.DOCTOR);
        bridge.onUserCreated(lab.tenant, person, PlatformRole.LAB_TECHNICIAN);

        assertThat(permissions.forTenant(clinic.tenant.getId(), person).privileges()).contains("clinical.encounter.sign");
        assertThat(permissions.forTenant(lab.tenant.getId(), person).privileges())
                .contains("lab.result.enter").doesNotContain("clinical.encounter.sign");
    }

    @Test
    void extraRolesWidenWithGrantAndNarrowWithDeny() {
        Fx fx = tenantWithAdmin("CLINIC");
        UUID doctor = UUID.randomUUID();
        bridge.onUserCreated(fx.tenant, doctor, PlatformRole.DOCTOR);
        PermissionSet admin = adminSet(fx);

        AuthzRole widen = roleAdmin.create(fx.tenant, new CreateRole("invoices", "Invoices", null, null,
                Map.of("billing.invoice.view", DeltaEffect.GRANT)), admin, fx.admin);
        // The admin template does not hold clinical privileges, so a narrowing role on them has to come from someone who does:
        AuthzRole narrow = roleAdmin.create(fx.tenant, new CreateRole("no_signing", "No signing", null, null,
                Map.of("clinical.encounter.sign", DeltaEffect.DENY)), withAll(admin, "clinical.encounter.sign"), fx.admin);

        Membership m = memberships.find(fx.tenant.getId(), doctor).orElseThrow();
        UUID doctorRole = roleId(fx, "doctor");
        memberships.replaceAssignments(fx.tenant, m.getId(), new AssignmentChange(doctorRole, List.of(
                new Extra(widen.getId(), null, null), new Extra(narrow.getId(), null, null))),
                withAll(admin, "clinical.encounter.sign"), fx.admin);

        PermissionSet s = permissions.forTenant(fx.tenant.getId(), doctor);
        assertThat(s.privileges()).contains("billing.invoice.view", "clinical.encounter.chart")
                .doesNotContain("clinical.encounter.sign");
        assertThat(s.roles()).first().isEqualTo("doctor");
    }

    @Test
    void derivedRoleCanBeThePrimaryAndKeepsTemplateUpdates() {
        Fx fx = tenantWithAdmin("CLINIC");
        UUID nurse = UUID.randomUUID();
        bridge.onUserCreated(fx.tenant, nurse, PlatformRole.NURSE);
        UUID nurseTemplate = jdbc.queryForObject("SELECT id FROM authz_role_templates WHERE tenant_type='CLINIC' AND code='nurse'", UUID.class);

        AuthzRole noVitals = roleAdmin.create(fx.tenant, new CreateRole("nurse_lite", "Nurse (no vitals)", null, nurseTemplate,
                Map.of("clinical.vitals.record", DeltaEffect.DENY)), withAll(adminSet(fx), "clinical.vitals.record"), fx.admin);
        Membership m = memberships.find(fx.tenant.getId(), nurse).orElseThrow();
        memberships.replaceAssignments(fx.tenant, m.getId(), new AssignmentChange(noVitals.getId(), List.of()),
                withAll(adminSet(fx), "clinical.vitals.record", "clinical.encounter.view", "lab.result.view", "referral.request.view"), fx.admin);

        PermissionSet s = permissions.forTenant(fx.tenant.getId(), nurse);
        assertThat(s.primaryRole()).isEqualTo("nurse_lite");
        assertThat(s.privileges()).contains("clinical.encounter.view").doesNotContain("clinical.vitals.record");
    }

    @Test
    void expiredExtrasStopCounting() {
        Fx fx = tenantWithAdmin("CLINIC");
        UUID user = UUID.randomUUID();
        bridge.onUserCreated(fx.tenant, user, PlatformRole.RECEPTIONIST);
        AuthzRole locum = roleAdmin.create(fx.tenant, new CreateRole("reports", "Reports", null, null,
                Map.of("platform.report.view", DeltaEffect.GRANT)), adminSet(fx), fx.admin);
        Membership m = memberships.find(fx.tenant.getId(), user).orElseThrow();
        memberships.replaceAssignments(fx.tenant, m.getId(), new AssignmentChange(roleId(fx, "receptionist"),
                List.of(new Extra(locum.getId(), Instant.now().plus(1, ChronoUnit.DAYS), null))), adminSet(fx), fx.admin);
        assertThat(permissions.forTenant(fx.tenant.getId(), user).privileges()).contains("platform.report.view");

        jdbc.update("UPDATE authz_role_assignments SET valid_from = now() - interval '2 days', valid_to = now() - interval '1 day' WHERE role_id = ?", locum.getId());
        assertThat(permissions.forTenant(fx.tenant.getId(), user).privileges()).doesNotContain("platform.report.view");
    }

    @Test
    void suspendedMembersAndInactiveTenantsHoldNothing() {
        Fx fx = tenantWithAdmin("CLINIC");
        UUID user = UUID.randomUUID();
        bridge.onUserCreated(fx.tenant, user, PlatformRole.RECEPTIONIST);
        bridge.onUserStatusChanged(fx.tenant, user, "INACTIVE");
        assertThat(permissions.forTenant(fx.tenant.getId(), user).status()).isEqualTo(PermissionSet.Status.MEMBERSHIP_INACTIVE);

        jdbc.update("UPDATE tenants SET status = 'suspended' WHERE id = ?", fx.tenant.getId());
        assertThat(permissions.forTenant(fx.tenant.getId(), fx.admin).status()).isEqualTo(PermissionSet.Status.TENANT_INACTIVE);
    }

    // ── guardrails ───────────────────────────────────────────────────────

    @Test
    void customRolesCannotBePrimary() {
        Fx fx = tenantWithAdmin("CLINIC");
        AuthzRole custom = roleAdmin.create(fx.tenant, new CreateRole("custom_x", "X", null, null,
                Map.of("platform.report.view", DeltaEffect.GRANT)), adminSet(fx), fx.admin);
        Membership m = memberships.find(fx.tenant.getId(), fx.admin).orElseThrow();
        assertRule(Rule.PRIMARY_KIND, () -> memberships.replaceAssignments(fx.tenant, m.getId(),
                new AssignmentChange(custom.getId(), List.of()), adminSet(fx), fx.admin));
    }

    @Test
    void privilegesMustFitTheTenantAndThePlatformKeepsItsOwn() {
        Fx fx = tenantWithAdmin("CLINIC");
        PermissionSet godlike = withAll(adminSet(fx), "lab.result.enter", "platform.tenant.view");
        assertRule(Rule.TENANT_TYPE, () -> roleAdmin.create(fx.tenant, new CreateRole("bad1", "Bad", null, null,
                Map.of("lab.result.enter", DeltaEffect.GRANT)), godlike, fx.admin));
        assertRule(Rule.PLATFORM_ONLY, () -> roleAdmin.create(fx.tenant, new CreateRole("bad2", "Bad", null, null,
                Map.of("platform.tenant.view", DeltaEffect.GRANT)), godlike, fx.admin));
        assertRule(Rule.UNKNOWN_PRIVILEGE, () -> roleAdmin.create(fx.tenant, new CreateRole("bad3", "Bad", null, null,
                Map.of("made.up.thing", DeltaEffect.GRANT)), godlike, fx.admin));
    }

    @Test
    void adminsAssignAnyRoleToOthersButCannotEscalateThemselves() {
        Fx fx = tenantWithAdmin("CLINIC");
        // The clinic admin template has no clinical privileges, yet must be able to onboard a doctor.
        UUID user = UUID.randomUUID();
        bridge.onUserCreated(fx.tenant, user, PlatformRole.RECEPTIONIST);
        Membership other = memberships.find(fx.tenant.getId(), user).orElseThrow();
        memberships.replaceAssignments(fx.tenant, other.getId(),
                new AssignmentChange(roleId(fx, "doctor"), List.of()), adminSet(fx), fx.admin);
        assertThat(permissions.forTenant(fx.tenant.getId(), user).privileges()).contains("clinical.encounter.sign");

        // …but cannot make themselves one.
        Membership self = memberships.find(fx.tenant.getId(), fx.admin).orElseThrow();
        assertRule(Rule.ESCALATION, () -> memberships.replaceAssignments(fx.tenant, self.getId(),
                new AssignmentChange(roleId(fx, "clinic_admin"), List.of(new Extra(roleId(fx, "doctor"), null, null))),
                adminSet(fx), fx.admin));

        // …nor widen a role they hold.
        AuthzRole mine = roleAdmin.create(fx.tenant, new CreateRole("mine", "Mine", null, null,
                Map.of("platform.report.view", DeltaEffect.GRANT)), adminSet(fx), fx.admin);
        memberships.replaceAssignments(fx.tenant, self.getId(), new AssignmentChange(roleId(fx, "clinic_admin"),
                List.of(new Extra(mine.getId(), null, null))), adminSet(fx), fx.admin);
        assertRule(Rule.ESCALATION, () -> roleAdmin.update(fx.tenant, mine.getId(), new UpdateRole(null, null,
                Map.of("clinical.encounter.sign", DeltaEffect.GRANT)), adminSet(fx), fx.admin));
        assertThat(permissions.forTenant(fx.tenant.getId(), fx.admin).privileges()).doesNotContain("clinical.encounter.sign");
    }

    @Test
    void theLastAdministratorCannotBeRemovedAndNothingChanges() {
        Fx fx = tenantWithAdmin("CLINIC");
        Membership m = memberships.find(fx.tenant.getId(), fx.admin).orElseThrow();
        // Assistant holds only privileges the admin holds, so this is a pure demotion.
        assertRule(Rule.LAST_ADMIN, () -> memberships.replaceAssignments(fx.tenant, m.getId(),
                new AssignmentChange(roleId(fx, "assistant"), List.of()), adminSet(fx), fx.admin));
        assertThat(permissions.forTenant(fx.tenant.getId(), fx.admin).primaryRole()).isEqualTo("clinic_admin");

        // A narrowing extra that removes member management is refused the same way.
        AuthzRole strip = roleAdmin.create(fx.tenant, new CreateRole("strip", "Strip", null, null,
                Map.of("platform.member.manage", DeltaEffect.DENY)), adminSet(fx), fx.admin);
        assertRule(Rule.LAST_ADMIN, () -> memberships.replaceAssignments(fx.tenant, m.getId(),
                new AssignmentChange(roleId(fx, "clinic_admin"), List.of(new Extra(strip.getId(), null, null))), adminSet(fx), fx.admin));
    }

    @Test
    void templatesAreImmutableAndRolesInUseStay() {
        Fx fx = tenantWithAdmin("CLINIC");
        assertRule(Rule.TEMPLATE_IMMUTABLE, () -> roleAdmin.update(fx.tenant, roleId(fx, "nurse"),
                new UpdateRole("Renamed", null, null), adminSet(fx), fx.admin));

        AuthzRole extra = roleAdmin.create(fx.tenant, new CreateRole("held", "Held", null, null,
                Map.of("platform.report.view", DeltaEffect.GRANT)), adminSet(fx), fx.admin);
        Membership m = memberships.find(fx.tenant.getId(), fx.admin).orElseThrow();
        memberships.replaceAssignments(fx.tenant, m.getId(), new AssignmentChange(roleId(fx, "clinic_admin"),
                List.of(new Extra(extra.getId(), null, null))), adminSet(fx), fx.admin);
        assertRule(Rule.ROLE_IN_USE, () -> roleAdmin.delete(fx.tenant, extra.getId(), fx.admin));
        assertRule(Rule.DUPLICATE_CODE, () -> roleAdmin.create(fx.tenant, new CreateRole("held", "Again", null, null,
                Map.of()), adminSet(fx), fx.admin));
    }

    @Test
    void customRolesTurnReadOnlyWhenThePlanLosesTheFeatureButKeepWorking() {
        Fx fx = tenantWithAdmin("CLINIC");
        UUID user = UUID.randomUUID();
        bridge.onUserCreated(fx.tenant, user, PlatformRole.RECEPTIONIST);
        AuthzRole custom = roleAdmin.create(fx.tenant, new CreateRole("reporting", "Reporting", null, null,
                Map.of("platform.report.view", DeltaEffect.GRANT)), adminSet(fx), fx.admin);
        Membership m = memberships.find(fx.tenant.getId(), user).orElseThrow();
        memberships.replaceAssignments(fx.tenant, m.getId(), new AssignmentChange(roleId(fx, "receptionist"),
                List.of(new Extra(custom.getId(), null, null))), adminSet(fx), fx.admin);

        String off = "UPDATE plan_features SET enabled = ? WHERE feature_id = (SELECT id FROM features WHERE code = 'authz.custom_roles') " +
                "AND plan_id = (SELECT id FROM plans WHERE code = 'free')";
        jdbc.update(off, false);
        try {
            assertRule(Rule.ENTITLEMENT, () -> roleAdmin.create(fx.tenant, new CreateRole("another", "Another", null, null,
                    Map.of()), adminSet(fx), fx.admin));
            assertRule(Rule.ENTITLEMENT, () -> roleAdmin.update(fx.tenant, custom.getId(),
                    new UpdateRole("Renamed", null, null), adminSet(fx), fx.admin));
            assertThat(permissions.forTenant(fx.tenant.getId(), user).privileges()).contains("platform.report.view");
        } finally {
            jdbc.update(off, true);
        }
    }

    @Test
    void explainNamesTheRoleThatDecided() {
        Fx fx = tenantWithAdmin("CLINIC");
        Membership m = memberships.find(fx.tenant.getId(), fx.admin).orElseThrow();
        var yes = memberships.explain(fx.tenant, m.getId(), "platform.role.manage");
        var no = memberships.explain(fx.tenant, m.getId(), "clinical.encounter.sign");
        assertThat(yes.allowed()).isTrue();
        assertThat(yes.reasons()).anyMatch(r -> r.contains("clinic_admin"));
        assertThat(no.allowed()).isFalse();
        assertThat(no.reasons()).contains("No held role grants it");
    }

    // ── decision bean and cache ──────────────────────────────────────────

    @Test
    void decisionsNameTheLayerAndFollowChangesThroughTheCache() {
        Fx fx = tenantWithAdmin("CLINIC");
        UUID user = UUID.randomUUID();
        bridge.onUserCreated(fx.tenant, user, PlatformRole.RECEPTIONIST);
        var auth = token(user, fx.tenant.getTenantId());

        assertThat(authz.decide(auth, "patient.record.register").allowed()).isTrue();
        Decision denied = authz.decide(auth, "clinical.encounter.sign");
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.layer()).isEqualTo(Decision.Layer.PRIVILEGE);
        assertThat(authz.decide(auth, "no.such.privilege").layer()).isEqualTo(Decision.Layer.CONTEXT);
        assertThat(authz.decide(token(UUID.randomUUID(), fx.tenant.getTenantId()), "patient.record.view").reason())
                .isEqualTo("no_membership");

        // The set is cached; a committed change drops it.
        assertThat(authz.has(auth, "platform.report.view")).isFalse();
        AuthzRole reports = roleAdmin.create(fx.tenant, new CreateRole("rep", "Rep", null, null,
                Map.of("platform.report.view", DeltaEffect.GRANT)), adminSet(fx), fx.admin);
        Membership m = memberships.find(fx.tenant.getId(), user).orElseThrow();
        memberships.replaceAssignments(fx.tenant, m.getId(), new AssignmentChange(roleId(fx, "receptionist"),
                List.of(new Extra(reports.getId(), null, null))), adminSet(fx), fx.admin);
        assertThat(authz.has(auth, "platform.report.view")).isTrue();

        memberships.setStatus(m, MembershipStatus.SUSPENDED);
        assertThat(authz.decide(auth, "patient.record.view").layer()).isEqualTo(Decision.Layer.CONTEXT);
    }

    @Test
    void platformPrivilegesComeFromThePlatformMembership() {
        Fx fx = tenantWithAdmin("CLINIC");
        var auth = token(fx.admin, fx.tenant.getTenantId());
        assertThat(authz.has(auth, "platform.tenant.view")).isFalse();

        Membership platform = memberships.ensure(null, fx.admin, null);
        UUID support = jdbc.queryForObject("SELECT id FROM authz_roles WHERE tenant_id IS NULL AND code = 'support'", UUID.class);
        jdbc.update("INSERT INTO authz_role_assignments (membership_id, role_id, is_primary) VALUES (?, ?, true)",
                platform.getId(), support);
        assertThat(permissions.forPlatform(fx.admin).privileges())
                .containsExactlyInAnyOrder("platform.tenant.view", "platform.support.session");
    }

    // ── fixtures ─────────────────────────────────────────────────────────

    record Fx(Tenant tenant, UUID admin) {
    }

    private Fx tenantWithAdmin(String type) {
        UUID id = UUID.randomUUID();
        String slug = "az" + id.toString().substring(0, 8);
        jdbc.update("INSERT INTO tenants (id, tenant_id, name, display_name, email, slug, type) VALUES (?,?,?,?,?,?,?)",
                id, UUID.randomUUID().toString(), slug, slug, slug + "@t.test", slug, type);
        Tenant tenant = tenants.findById(id).orElseThrow();
        UUID admin = UUID.randomUUID();
        bridge.onTenantCreated(tenant, admin);
        return new Fx(tenant, admin);
    }

    private PermissionSet adminSet(Fx fx) {
        return permissions.forTenant(fx.tenant.getId(), fx.admin);
    }

    /** The admin's real set plus extra codes, to act as an editor who holds them. */
    private static PermissionSet withAll(PermissionSet base, String... more) {
        Set<String> all = new HashSet<>(base.privileges());
        all.addAll(List.of(more));
        return new PermissionSet(base.tenantId(), base.userSub(), base.membershipId(), base.status(), all,
                base.primaryRole(), base.roles(), base.computedAt());
    }

    private UUID roleId(Fx fx, String code) {
        return jdbc.queryForObject("SELECT id FROM authz_roles WHERE tenant_id = ? AND code = ?", UUID.class, fx.tenant.getId(), code);
    }

    private static JwtAuthenticationToken token(UUID sub, String orgId) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(sub.toString())
                .claim("organization", Map.of("org", Map.of("id", orgId))).build();
        return new JwtAuthenticationToken(jwt);
    }

    private static void assertRule(Rule rule, org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(AuthzRuleException.class)
                .satisfies(e -> assertThat(((AuthzRuleException) e).getRule()).isEqualTo(rule));
    }
}
