package com.flowbinary.platformservice.authz;

import com.flowbinary.platformservice.authz.sdk.Authz;
import com.flowbinary.platformservice.authz.sdk.AuthzSdkConfig;
import com.flowbinary.platformservice.authz.sdk.PermissionCache;
import com.flowbinary.platformservice.authz.service.*;
import com.flowbinary.platformservice.authz.service.AuthzManifestService.Outcome;
import com.flowbinary.platformservice.config.AppProperties;
import com.flowbinary.platformservice.config.JpaConfig;
import com.flowbinary.platformservice.model.Tenant;
import com.flowbinary.platformservice.repository.TenantRepository;
import com.flowbinary.platformservice.security.TenantContextResolver;
import com.flowbinary.platformservice.service.AuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The manifest flow against a real PostgreSQL schema: a service declares privileges, an operator approves a
 * template grant, and the change reaches real permission sets. Opt-in like {@link AuthzIT}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "AUTHZ_IT_DB_URL", matches = ".+")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@Import({JpaConfig.class, AppProperties.class, AuditService.class, TenantContextResolver.class, AuthzSdkConfig.class,
        EffectivePermissionService.class, TenantFeatures.class, PermissionCache.class, AuthzEvents.class,
        TenantRoleProvisioner.class, MembershipService.class, AuthzGuardrails.class, TenantOnboarding.class, Authz.class,
        AuthzManifestService.class, TemplateProposalService.class, AuthzServiceIdentity.class})
class AuthzManifestIT {

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> System.getenv("AUTHZ_IT_DB_URL"));
        r.add("spring.datasource.username", () -> Optional.ofNullable(System.getenv("AUTHZ_IT_DB_USER")).orElse(System.getProperty("user.name")));
        r.add("spring.datasource.password", () -> Optional.ofNullable(System.getenv("AUTHZ_IT_DB_PASSWORD")).orElse(""));
        r.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired TenantRepository tenants;
    @Autowired AuthzManifestService manifests;
    @Autowired TemplateProposalService proposals;
    @Autowired AuthzServiceIdentity identity;
    @Autowired EffectivePermissionService permissions;
    @Autowired TenantOnboarding onboarding;
    @Autowired TenantRoleProvisioner provisioner;
    @Autowired Authz authz;

    private static String uniq() {
        return "s" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private static String manifest(String service, String version, String privileges, String grants) {
        return "{\"service\":\"" + service + "\",\"version\":\"" + version + "\",\"privileges\":[" + privileges
                + "],\"templateGrants\":[" + grants + "]}";
    }

    private static String priv(String service, String action, String extra) {
        return "{\"code\":\"" + service + ".study." + action + "\",\"name\":\"" + action + "\",\"tenantTypes\":[\"LAB\"]" + extra + "}";
    }

    private String status(String code) {
        return jdbc.queryForObject("SELECT status FROM authz_privileges WHERE code = ?", String.class, code);
    }

    // ── the manifest ─────────────────────────────────────────────────────

    @Test
    void aFirstManifestCreatesPrivilegesAndOpensProposalsAndARepeatChangesNothing() {
        String svc = uniq();
        String body = manifest(svc, "1", priv(svc, "view", "") + "," + priv(svc, "sign", ",\"phi\":true,\"clinicalWrite\":true,\"risk\":\"HIGH\""),
                "{\"tenantType\":\"LAB\",\"template\":\"lab_technician\",\"privileges\":[\"" + svc + ".study.view\"]}");

        var first = manifests.accept(svc, body, "client-" + svc);
        assertThat(first.outcome()).isEqualTo(Outcome.ACCEPTED);
        assertThat(first.created()).containsExactlyInAnyOrder(svc + ".study.view", svc + ".study.sign");
        assertThat(first.proposalsOpened()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT risk || ':' || phi || ':' || clinical_write FROM authz_privileges WHERE code = ?", String.class,
                svc + ".study.sign")).isEqualTo("HIGH:true:true");

        var again = manifests.accept(svc, body, "client-" + svc);
        assertThat(again.outcome()).isEqualTo(Outcome.UNCHANGED);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM authz_manifests WHERE service = ?", Integer.class, svc)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM authz_template_proposals WHERE service = ?", Integer.class, svc)).isEqualTo(1);
    }

    @Test
    void updatesDeprecationAndReactivationAreTracked() {
        String svc = uniq();
        manifests.accept(svc, manifest(svc, "1", priv(svc, "view", "") + "," + priv(svc, "sign", ""), ""), "c");

        var renamed = manifests.accept(svc, manifest(svc, "2", priv(svc, "view", ",\"description\":\"changed\"") + "," + priv(svc, "sign", ""), ""), "c");
        assertThat(renamed.updated()).containsExactly(svc + ".study.view");

        var dropped = manifests.accept(svc, manifest(svc, "3", priv(svc, "view", ",\"description\":\"changed\""), ""), "c");
        assertThat(dropped.deprecated()).containsExactly(svc + ".study.sign");
        assertThat(status(svc + ".study.sign")).isEqualTo("DEPRECATED");
        assertThat(status(svc + ".study.view")).isEqualTo("ACTIVE");

        var back = manifests.accept(svc, manifest(svc, "4", priv(svc, "view", ",\"description\":\"changed\"") + "," + priv(svc, "sign", ""), ""), "c");
        assertThat(back.reactivated()).containsExactly(svc + ".study.sign");
        assertThat(status(svc + ".study.sign")).isEqualTo("ACTIVE");
    }

    @Test
    void anInvalidManifestChangesNothingAndIsRecorded() {
        String svc = uniq();
        manifests.accept(svc, manifest(svc, "1", priv(svc, "view", ",\"phi\":true"), ""), "c");

        var bad = manifests.accept(svc, manifest(svc, "2", priv(svc, "view", ",\"phi\":false") + ",{\"code\":\"lab.x.y\",\"name\":\"n\",\"tenantTypes\":[\"LAB\"]}", ""), "c");
        assertThat(bad.outcome()).isEqualTo(Outcome.REJECTED);
        assertThat(bad.errors()).anyMatch(e -> e.contains("phi cannot be lowered")).anyMatch(e -> e.contains("outside the"));
        assertThat(jdbc.queryForObject("SELECT phi FROM authz_privileges WHERE code = ?", Boolean.class, svc + ".study.view")).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM authz_manifests WHERE service = ? AND outcome = 'REJECTED'", Integer.class, svc)).isEqualTo(1);

        var garbage = manifests.accept(svc, "{not json", "c");
        assertThat(garbage.outcome()).isEqualTo(Outcome.REJECTED);
        var typo = manifests.accept(svc, manifest(svc, "3", priv(svc, "view", ",\"clinicalWrte\":true"), ""), "c");
        assertThat(typo.outcome()).isEqualTo(Outcome.REJECTED);                         // unknown fields are errors
    }

    @Test
    void aServiceCannotTouchAnotherServicesPrivilegesOrPlatform() {
        String a = uniq(), b = uniq();
        manifests.accept(a, manifest(a, "1", priv(a, "view", ""), ""), "ca");
        var steal = manifests.accept(b, manifest(b, "1", priv(a, "view", ",\"risk\":\"LOW\""), ""), "cb");
        assertThat(steal.outcome()).isEqualTo(Outcome.REJECTED);
        assertThat(steal.errors()).anyMatch(e -> e.contains("outside the '" + b + "' namespace"));
        var platform = manifests.accept("platform", manifest("platform", "1", "{\"code\":\"platform.x.y\",\"name\":\"n\",\"tenantTypes\":[\"LAB\"]}", ""), "cx");
        assertThat(platform.outcome()).isEqualTo(Outcome.REJECTED);
    }

    // ── proposals ────────────────────────────────────────────────────────

    @Test
    void approvingAProposalChangesRealPermissionsAndRejectingDoesNot() {
        String svc = uniq();
        String view = svc + ".study.view", sign = svc + ".study.sign";
        manifests.accept(svc, manifest(svc, "1", priv(svc, "view", "") + "," + priv(svc, "sign", ""),
                "{\"tenantType\":\"LAB\",\"template\":\"lab_technician\",\"privileges\":[\"" + view + "\",\"" + sign + "\"]}"), "c");

        Tenant lab = lab();
        UUID tech = UUID.randomUUID();
        memberWithTemplate(lab, tech, "lab_technician");
        assertThat(permissions.forTenant(lab.getId(), tech).privileges()).doesNotContain(view, sign);   // nothing before approval

        var open = proposals.list("PENDING").stream().filter(p -> p.service().equals(svc)).toList();
        assertThat(open).hasSize(2);
        UUID operator = UUID.randomUUID();
        int versionBefore = jdbc.queryForObject("SELECT version FROM authz_role_templates WHERE tenant_type='LAB' AND code='lab_technician'", Integer.class);

        var approved = proposals.approve(open.stream().filter(p -> p.privilege().equals(view)).findFirst().orElseThrow().id(), operator);
        assertThat(approved.status()).isEqualTo("APPROVED");
        var rejected = proposals.reject(open.stream().filter(p -> p.privilege().equals(sign)).findFirst().orElseThrow().id(), operator, "too broad");
        assertThat(rejected.status()).isEqualTo("REJECTED");

        assertThat(jdbc.queryForObject("SELECT version FROM authz_role_templates WHERE tenant_type='LAB' AND code='lab_technician'", Integer.class))
                .isEqualTo(versionBefore + 1);
        PermissionSet after = permissions.forTenant(lab.getId(), tech);
        assertThat(after.privileges()).contains(view).doesNotContain(sign);
        // a role derived from the template gets it too, without touching tenant data
        assertThat(jdbc.queryForObject("SELECT count(*) FROM authz_role_template_privileges WHERE privilege_code = ?", Integer.class, view)).isEqualTo(1);

        assertThatThrownBy(() -> proposals.approve(approved.id(), operator)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aRepeatedManifestDoesNotReopenADecidedOrPendingProposal() {
        String svc = uniq();
        String grant = "{\"tenantType\":\"LAB\",\"template\":\"phlebotomist\",\"privileges\":[\"" + svc + ".study.view\"]}";
        manifests.accept(svc, manifest(svc, "1", priv(svc, "view", ""), grant), "c");
        var second = manifests.accept(svc, manifest(svc, "2", priv(svc, "view", ""), grant), "c");
        assertThat(second.proposalsOpened()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM authz_template_proposals WHERE service = ?", Integer.class, svc)).isEqualTo(1);
    }

    @Test
    void aDeprecatedPrivilegeCannotBeApproved() {
        String svc = uniq();
        manifests.accept(svc, manifest(svc, "1", priv(svc, "view", "") + "," + priv(svc, "sign", ""),
                "{\"tenantType\":\"LAB\",\"template\":\"phlebotomist\",\"privileges\":[\"" + svc + ".study.sign\"]}"), "c");
        manifests.accept(svc, manifest(svc, "2", priv(svc, "view", ""), ""), "c");           // sign is dropped
        UUID id = proposals.list("PENDING").stream().filter(p -> p.service().equals(svc)).findFirst().orElseThrow().id();
        assertThatThrownBy(() -> proposals.approve(id, UUID.randomUUID())).isInstanceOf(IllegalStateException.class).hasMessageContaining("deprecated");
    }

    @Test
    void theServiceSummaryShowsCountsAndPendingWork() {
        String svc = uniq();
        manifests.accept(svc, manifest(svc, "7", priv(svc, "view", "") + "," + priv(svc, "sign", ""),
                "{\"tenantType\":\"LAB\",\"template\":\"phlebotomist\",\"privileges\":[\"" + svc + ".study.view\"]}"), "c");
        var summary = proposals.services().stream().filter(s -> s.service().equals(svc)).findFirst().orElseThrow();
        assertThat(summary.privileges()).isEqualTo(2);
        assertThat(summary.pendingProposals()).isEqualTo(1);
        assertThat(summary.lastVersion()).isEqualTo("7");
    }

    // ── the decision bean knows a new privilege at once ──────────────────

    @Test
    void aPrivilegeDeclaredAfterTheCatalogueWasCachedIsKnownImmediately() {
        Tenant lab = lab();
        UUID admin = UUID.randomUUID();
        onboarding.onTenantCreated(lab, admin);
        var auth = token(admin, lab.getTenantId());
        assertThat(authz.decide(auth, "nothing.here.yet").reason()).isEqualTo("unknown privilege");     // warms the catalogue cache

        String svc = uniq();
        manifests.accept(svc, manifest(svc, "1", priv(svc, "view", ""), ""), "c");
        Decision known = decide(auth, svc + ".study.view");
        assertThat(known.reason()).isEqualTo("privilege not held");                                        // known now, just not held
    }

    private record Decision(String reason) {
    }

    private Decision decide(JwtAuthenticationToken auth, String privilege) {
        return new Decision(authz.decide(auth, privilege).reason());
    }

    // ── who may declare ──────────────────────────────────────────────────

    @Test
    void onlyTheClientThatOwnsTheServiceModuleMayDeclare() {
        String svc = uniq(), client = "client-" + svc;
        registerModule(svc, client, "ACTIVE");

        assertThat(identity.requireOwner(service(client), svc)).isEqualTo(client);
        assertThatThrownBy(() -> identity.requireOwner(service("someone-else"), svc)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> identity.requireOwner(service(client), uniq())).isInstanceOf(AccessDeniedException.class);   // unknown service: same answer
        assertThatThrownBy(() -> identity.requireOwner(userOfClient(client), svc)).isInstanceOf(AccessDeniedException.class); // a user, not a service
        jdbc.update("UPDATE menu_modules SET status = 'SUSPENDED' WHERE code = ?", svc);
        assertThatThrownBy(() -> identity.requireOwner(service(client), svc)).isInstanceOf(AccessDeniedException.class);
        jdbc.update("UPDATE menu_modules SET status = 'PENDING' WHERE code = ?", svc);
        assertThatThrownBy(() -> identity.requireOwner(service(client), svc)).isInstanceOf(AccessDeniedException.class);
    }

    // ── fixtures ─────────────────────────────────────────────────────────

    private Tenant lab() {
        UUID id = UUID.randomUUID();
        String slug = "mf" + id.toString().substring(0, 8);
        jdbc.update("INSERT INTO tenants (id, tenant_id, name, display_name, email, slug, type) VALUES (?,?,?,?,?,?,'LAB')",
                id, UUID.randomUUID().toString(), slug, slug, slug + "@t.test", slug);
        return tenants.findById(id).orElseThrow();
    }

    private void memberWithTemplate(Tenant tenant, UUID sub, String template) {
        provisioner.provision(tenant);
        UUID roleId = jdbc.queryForObject("SELECT id FROM authz_roles WHERE tenant_id = ? AND code = ?", UUID.class, tenant.getId(), template);
        onboarding.onMemberCreated(tenant, sub, roleId, null);
    }

    private void registerModule(String code, String client, String status) {
        jdbc.update("INSERT INTO menu_modules (code, name, application_id, owner_client_id, allowed_route_prefixes, privilege_module_code, status) " +
                "SELECT ?, ?, id, ?, ARRAY[?], ?, ? FROM applications WHERE code = 'PROVIDER_CONSOLE'", code, code, client, "/" + code, code, status);
    }

    private static JwtAuthenticationToken service(String client) {
        return new JwtAuthenticationToken(Jwt.withTokenValue("t").header("alg", "none").subject("svc")
                .claim("azp", client).claim("preferred_username", "service-account-" + client).build());
    }

    private static JwtAuthenticationToken userOfClient(String client) {
        return new JwtAuthenticationToken(Jwt.withTokenValue("t").header("alg", "none").subject("alice")
                .claim("azp", client).claim("preferred_username", "alice").build());
    }

    private static JwtAuthenticationToken token(UUID sub, String orgId) {
        return new JwtAuthenticationToken(Jwt.withTokenValue("t").header("alg", "none").subject(sub.toString())
                .claim("organization", Map.of("org", Map.of("id", orgId))).build());
    }
}
