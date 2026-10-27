package com.flowbinary.platformservice.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Map;
import java.util.UUID;

/** Tenants, users and tokens for integration tests that talk to a real schema. */
final class MenuItFixtures {

    record Person(UUID authId, UUID userId, String orgId, UUID tenantId) {
    }

    private final JdbcTemplate jdbc;

    MenuItFixtures(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Two global PROVIDER_CONSOLE items the functional tests edit, hide and grant on:
     * {@code fx_dash} (no privilege, every system role) and {@code fx_admin_users}
     * (needs {@code user.manage}). They stand in for the retired V5 items, so the tests
     * do not depend on whatever the catalogue seed ships. Idempotent.
     */
    static void ensureItems(JdbcTemplate jdbc) {
        jdbc.update("INSERT INTO menu_items (application_id, code, label, route, type, sort_order, depth, path, required_privilege_id) " +
                "SELECT a.id, v.code, v.label, v.route, 'LINK', 900, 0, v.code, p.id " +
                "FROM applications a CROSS JOIN (VALUES ('fx_dash','Fixture dashboard','/fx-dash',NULL),('fx_admin_users','Fixture users','/fx-users','user.manage')) " +
                "AS v(code,label,route,priv) LEFT JOIN privileges p ON p.code = v.priv " +
                "WHERE a.code = 'PROVIDER_CONSOLE' ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO menu_role_access (menu_item_id, role_id) SELECT m.id, r.id FROM menu_items m, roles r " +
                "WHERE m.code IN ('fx_dash','fx_admin_users') AND r.tenant_id IS NULL ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO menu_plan_access (menu_item_id, plan) SELECT m.id, pl.plan FROM menu_items m, " +
                "(VALUES ('free'),('starter'),('pro'),('enterprise')) AS pl(plan) " +
                "WHERE m.code IN ('fx_dash','fx_admin_users') ON CONFLICT DO NOTHING");
    }

    static String uniq(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 6);
    }

    /** @return {tenantId, orgId} */
    Object[] tenant() {
        UUID id = UUID.randomUUID();
        String org = UUID.randomUUID().toString();
        String slug = "t" + id.toString().substring(0, 8);
        jdbc.update("INSERT INTO tenants (id, tenant_id, name, display_name, email, slug) VALUES (?,?,?,?,?,?)",
                id, org, slug, slug, slug + "@t.test", slug);
        return new Object[]{id, org};
    }

    Person person(String platformRole, UUID tenantId, String orgId) {
        UUID auth = UUID.randomUUID(), uid = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, tenant_id, auth_user_id, role, email, username) VALUES (?,?,?,?,?,?)",
                uid, tenantId, auth, platformRole, auth + "@t.test", "u" + uid.toString().substring(0, 8));
        return new Person(auth, uid, orgId, tenantId);
    }

    Person person(String platformRole) {
        Object[] t = tenant();
        return person(platformRole, (UUID) t[0], (String) t[1]);
    }

    static JwtAuthenticationToken user(Person p) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(p.authId().toString())
                .claim("organization", Map.of("org", Map.of("id", p.orgId()))).build();
        return new JwtAuthenticationToken(jwt);
    }

    /** A Keycloak client-credentials token. */
    static JwtAuthenticationToken serviceToken(String clientId) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("svc")
                .claim("azp", clientId).claim("preferred_username", "service-account-" + clientId).build();
        return new JwtAuthenticationToken(jwt);
    }

    /** A user logged in through the module's client (authorization-code flow), not a service. */
    static JwtAuthenticationToken userOfClient(String clientId) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("alice")
                .claim("azp", clientId).claim("preferred_username", "alice").build();
        return new JwtAuthenticationToken(jwt);
    }
}
