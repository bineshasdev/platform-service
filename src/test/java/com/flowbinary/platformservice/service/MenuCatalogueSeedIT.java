package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.authz.model.AuthzRole;
import com.flowbinary.platformservice.authz.repository.AuthzRoleRepository;
import com.flowbinary.platformservice.authz.sdk.PermissionCache;
import com.flowbinary.platformservice.authz.service.EffectivePermissionService;
import com.flowbinary.platformservice.authz.service.Operators;
import com.flowbinary.platformservice.authz.service.TenantFeatures;
import com.flowbinary.platformservice.config.AppProperties;
import com.flowbinary.platformservice.config.JpaConfig;
import com.flowbinary.platformservice.dto.bootstrap.MenuEntry;
import com.flowbinary.platformservice.model.Tenant;
import com.flowbinary.platformservice.repository.TenantRepository;
import com.flowbinary.platformservice.security.TenantContextResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The seeded catalogue (V28) against Docs/design/menu-catalogue.md §11: what each built-in role sees at the
 * top of the left menu, per tenant type, once every phase is switched on and the plan is enterprise.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "MENU_IT_DB_URL", matches = ".+")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({JpaConfig.class, AppProperties.class, MenuResolutionService.class, MenuCatalogueCache.class,
        MenuCatalogueCompiler.class, MenuMetrics.class, MenuCatalogEvents.class, SettingsResolver.class,
        TenantContextResolver.class, EffectivePermissionService.class, TenantFeatures.class, PermissionCache.class, Operators.class})
class MenuCatalogueSeedIT {

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> System.getenv("MENU_IT_DB_URL"));
        r.add("spring.datasource.username", () -> Optional.ofNullable(System.getenv("MENU_IT_DB_USER")).orElse(System.getProperty("user.name")));
        r.add("spring.datasource.password", () -> Optional.ofNullable(System.getenv("MENU_IT_DB_PASSWORD")).orElse(""));
        r.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired MenuResolutionService resolution;
    @Autowired AuthzRoleRepository roles;
    @Autowired TenantRepository tenants;

    @BeforeEach
    void allPhasesOn() {
        jdbc.update("UPDATE menu_items SET is_active = true WHERE deleted_at IS NULL AND metadata ->> 'phase' IS NOT NULL");
    }

    @AfterEach
    void restore() {
        // the seed ships P1 on and everything later off
        jdbc.update("UPDATE menu_items SET is_active = false WHERE deleted_at IS NULL AND metadata ->> 'phase' IN ('P2','P3','P4')");
    }

    private Tenant tenant(String type) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO tenants (id, tenant_id, name, display_name, email, slug, type) VALUES (?,?,?,?,?,?,?)",
                id, UUID.randomUUID().toString(), "s" + id, "s" + id, id + "@t.test", "s" + id.toString().substring(0, 12), type);
        return tenants.findById(id).orElseThrow();
    }

    private Set<String> leftTop(String app, Tenant tenant, String templateCode) {
        AuthzTestSupport.provision(jdbc, tenant.getId());
        AuthzRole role = roles.findByTenantIdAndCode(tenant.getId(), templateCode).orElseThrow();
        List<MenuEntry> tree = resolution.resolveForRole(role,
                new MenuResolutionService.MenuContext(tenant, null, app, "enterprise", Set.of(), "en"));
        // other integration tests share the database and leave their own items behind; only the seed is under test
        Set<String> seeded = new HashSet<>(jdbc.queryForList(
                "SELECT m.code FROM menu_items m JOIN applications a ON a.id = m.application_id " +
                        "WHERE a.code = ? AND m.deleted_at IS NULL AND m.metadata ->> 'phase' IS NOT NULL", String.class, app));
        return MenuTreeBuilder.forPlacement(tree, "LEFT").stream().map(MenuEntry::getId)
                .filter(seeded::contains).collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<String> set(String... codes) {
        return new TreeSet<>(Arrays.asList(codes));
    }

    private static final String[] LAB_ALL = {"lab.orders", "lab.samples", "lab.home_collection", "lab.worklist", "lab.results", "lab.tat"};
    private static final String[] PHARM_ALL = {"pharmacy.queue", "pharmacy.substitutions", "pharmacy.inventory", "pharmacy.register",
            "pharmacy.deliveries", "pharmacy.adherence"};

    private static Set<String> with(String[] more, String... base) {
        Set<String> s = set(base);
        s.addAll(Arrays.asList(more));
        return s;
    }

    @Test
    void roleToLeftMenuMatchesTheCatalogue() {
        Tenant clinic = tenant("CLINIC"), lab = tenant("LAB"), store = tenant("STORE");
        Map<String, Set<String>> actual = new LinkedHashMap<>(), expected = new LinkedHashMap<>();

        record Case(String label, Tenant t, String role, Set<String> top) {
        }
        List<Case> cases = List.of(
                new Case("clinic_admin/clinic", clinic, "clinic_admin", set("home", "schedule", "patients", "billing", "claims", "network", "referrals", "ai_review", "reports", "compliance", "settings")),
                new Case("doctor/clinic", clinic, "doctor", set("home", "schedule", "patients", "encounters", "inbox", "orders", "telehealth", "monitoring", "referrals", "network", "ai_review", "reports")),
                new Case("nurse/clinic", clinic, "nurse", set("home", "schedule", "patients", "encounters", "inbox", "orders", "monitoring", "referrals", "network")),
                new Case("receptionist/clinic", clinic, "receptionist", set("home", "schedule", "patients", "billing")),
                new Case("medical_coder/clinic", clinic, "medical_coder", set("home", "coding", "ai_review", "encounters", "inbox", "reports")),
                new Case("billing_staff/clinic", clinic, "billing_staff", set("home", "patients", "billing", "claims", "reports", "settings")),
                new Case("compliance_officer/clinic", clinic, "compliance_officer", set("home", "compliance", "reports", "settings")),
                new Case("lab_admin/lab", lab, "lab_admin", with(LAB_ALL, "home", "billing", "reports", "settings")),
                new Case("lab_technician/lab", lab, "lab_technician", with(new String[]{"lab.samples", "lab.home_collection", "lab.worklist", "lab.results", "lab.tat"}, "home")),
                new Case("phlebotomist/lab", lab, "phlebotomist", set("home", "lab.samples", "lab.home_collection")),
                new Case("pharmacist/store", store, "pharmacist", with(PHARM_ALL, "home", "billing", "settings")));

        for (Case c : cases) {
            expected.put(c.label(), c.top());
            actual.put(c.label(), leftTop("PROVIDER_CONSOLE", c.t(), c.role()));
        }
        assertThat(actual).isEqualTo(expected);
    }
}
