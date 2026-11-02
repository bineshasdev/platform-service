package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.authz.sdk.PermissionCache;
import com.flowbinary.platformservice.authz.service.EffectivePermissionService;
import com.flowbinary.platformservice.authz.service.Operators;
import com.flowbinary.platformservice.authz.service.TenantFeatures;
import com.flowbinary.platformservice.config.AppProperties;
import com.flowbinary.platformservice.config.JpaConfig;
import com.flowbinary.platformservice.dto.module.ModuleRequest;
import com.flowbinary.platformservice.model.ManifestOutcome;
import com.flowbinary.platformservice.security.ModuleIdentity;
import com.flowbinary.platformservice.security.TenantContextResolver;
import com.flowbinary.platformservice.service.MenuAdminAccess.AdminContext;
import com.flowbinary.platformservice.service.MenuAdminAccess.ScopeKind;
import com.flowbinary.platformservice.service.MenuModuleSyncService.SyncResult;
import com.sun.net.httpserver.HttpServer;
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

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.flowbinary.platformservice.service.MenuItFixtures.uniq;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pulling manifests from a real (local) HTTP server: the happy path and every way it can go wrong.
 * Opt-in like the other ITs. Private hosts and plain http are enabled here — and only here —
 * because the server is on 127.0.0.1.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "MENU_IT_DB_URL", matches = ".+")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({JpaConfig.class, AppProperties.class, MenuScopeLoader.class, MenuChangeApplier.class,
        MenuHierarchyValidator.class, MenuReferenceLookup.class, MenuDraftService.class, MenuPublishService.class,
        MenuPreviewService.class, MenuCatalogueService.class, AuditService.class, MenuAdminAccess.class,
        MenuResolutionService.class, MenuQueryService.class, SettingsResolver.class, TenantContextResolver.class,
        EffectivePermissionService.class, TenantFeatures.class, PermissionCache.class, Operators.class, TemporaryMenuService.class, TemporaryMenuLifecycleService.class,
        MenuCatalogueCache.class, MenuCatalogueCompiler.class, MenuMetrics.class, MenuCatalogEvents.class,
        MenuModuleSyncService.class, MenuModuleRegistryService.class, MenuModulePuller.class,
        MenuModulePullScheduler.class, ModuleIdentity.class})
class MenuModulePullIT {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> System.getenv("MENU_IT_DB_URL"));
        r.add("spring.datasource.username", () -> Optional.ofNullable(System.getenv("MENU_IT_DB_USER")).orElse(System.getProperty("user.name")));
        r.add("spring.datasource.password", () -> Optional.ofNullable(System.getenv("MENU_IT_DB_PASSWORD")).orElse(""));
        r.add("spring.flyway.enabled", () -> "true");
        r.add("app.menu.cache.version-check", () -> "1ms");
        r.add("app.menu.modules.allow-private-hosts", () -> "true");
        r.add("app.menu.modules.allow-insecure-http", () -> "true");
        r.add("app.menu.modules.read-timeout", () -> "700ms");
        r.add("app.menu.modules.max-manifest-bytes", () -> "4000");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired MenuAdminAccess access;
    @Autowired MenuModuleRegistryService registry;
    @Autowired MenuModulePuller puller;
    @Autowired MenuModulePullScheduler scheduler;

    HttpServer server;
    final AtomicReference<String> body = new AtomicReference<>("");
    final AtomicInteger status = new AtomicInteger(200);
    final AtomicInteger delayMs = new AtomicInteger(0);
    final AtomicInteger hits = new AtomicInteger(0);
    AdminContext operator;
    String code, client, prefix;

    @BeforeEach
    void setUp() throws Exception {
        MenuItFixtures fx = new MenuItFixtures(jdbc);
        Object[] t = fx.tenant();
        operator = access.resolve(MenuItFixtures.user(fx.person("superadmin", (UUID) t[0], (String) t[1])),
                "PROVIDER_CONSOLE", ScopeKind.GLOBAL, null);
        code = uniq("p");
        client = "client-" + code;
        prefix = "/" + code;

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/menu", ex -> {
            hits.incrementAndGet();
            try {
                Thread.sleep(delayMs.get());
            } catch (InterruptedException ignored) {
            }
            if (status.get() == 302) {
                ex.getResponseHeaders().add("Location", "http://127.0.0.1:1/elsewhere");
                ex.sendResponseHeaders(302, -1);
            } else {
                byte[] out = body.get().getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().add("Content-Type", "application/json");
                ex.sendResponseHeaders(status.get(), out.length);
                ex.getResponseBody().write(out);
            }
            ex.close();
        });
        server.start();
        body.set(good("1", "A"));
        registry.register(operator, new ModuleRequest(code, "n", null, null, client,
                "http://127.0.0.1:" + server.getAddress().getPort() + "/menu", List.of(prefix), "billing"));
        registry.approve(operator, code);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private String good(String version, String label) {
        return "{\"schemaVersion\":1,\"module\":\"" + code + "\",\"version\":\"" + version + "\",\"items\":["
                + "{\"key\":\"a\",\"label\":\"" + label + "\",\"route\":\"" + prefix + "/a\",\"requiredPrivilege\":\"platform.subscription.view\"}]}";
    }

    private String label() {
        return jdbc.queryForObject("SELECT label FROM menu_items WHERE code=?", String.class, code + ".a");
    }

    private int auditRows(String action) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action=? AND entity_id=?", Integer.class, action, code);
    }

    private com.flowbinary.platformservice.dto.module.ModuleResponse module() {
        return registry.get(operator, code);
    }

    @Test
    void aPulledManifestIsValidatedAndAppliedExactlyLikeAPushedOne() {
        SyncResult r = puller.pull(code);

        assertThat(r.accepted()).isTrue();
        assertThat(r.created()).isEqualTo(1);
        assertThat(label()).isEqualTo("A");
        assertThat(module().health()).isEqualTo("UP");
        assertThat(jdbc.queryForObject("SELECT received_via FROM menu_module_manifests h JOIN menu_modules m ON m.id=h.module_id WHERE m.code=? AND h.outcome='ACCEPTED'",
                String.class, code)).isEqualTo("PULL");
        assertThat(module().nextAttemptAt()).isAfter(Instant.now().plus(Duration.ofMinutes(50)));    // the slow schedule

        // content that a push would reject is rejected on a pull too
        body.set(good("2", "B").replace(prefix + "/a", "/elsewhere/a"));
        SyncResult bad = puller.pull(code);
        assertThat(bad.outcome()).isEqualTo(ManifestOutcome.REJECTED);
        assertThat(label()).isEqualTo("A");
    }

    @Test
    void aFailingEndpointLeavesTheLastGoodMenuAndMarksTheModuleStale() {
        puller.pull(code);
        status.set(500);

        SyncResult r = puller.pull(code);

        assertThat(r.outcome()).isEqualTo(ManifestOutcome.FAILED);
        assertThat(r.errors().get(0)).contains("HTTP 500");
        assertThat(label()).isEqualTo("A");
        assertThat(module().health()).isEqualTo("STALE");
        assertThat(module().consecutiveFailures()).isEqualTo(1);
        assertThat(module().lastError()).contains("HTTP 500");

        // recovery
        status.set(200);
        body.set(good("2", "A2"));
        assertThat(puller.pull(code).accepted()).isTrue();
        assertThat(label()).isEqualTo("A2");
        assertThat(module().health()).isEqualTo("UP");
        assertThat(module().consecutiveFailures()).isZero();
    }

    @Test
    void aSlowEndpointTimesOutInsteadOfHangingTheCaller() {
        puller.pull(code);
        delayMs.set(2500);
        long start = System.nanoTime();

        SyncResult r = puller.pull(code);

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
        assertThat(r.outcome()).isEqualTo(ManifestOutcome.FAILED);
        assertThat(r.errors().get(0)).contains("timed out");
        assertThat(label()).isEqualTo("A");
    }

    @Test
    void anOversizedResponseAndARedirectAreRefused() {
        puller.pull(code);

        body.set("x".repeat(5000));
        assertThat(puller.pull(code).errors().get(0)).contains("larger than");

        status.set(302);
        assertThat(puller.pull(code).errors().get(0)).contains("HTTP 302");
        assertThat(label()).isEqualTo("A");
    }

    @Test
    void repeatedFailuresOpenTheCircuitBackOffAndEventuallyMarkTheModuleDown() {
        status.set(500);
        Instant first = null;
        for (int i = 1; i <= 5; i++) {
            puller.pull(code);
            if (i == 1) first = module().nextAttemptAt();
            if (i == 2) assertThat(module().nextAttemptAt()).isAfter(first);          // delay grows
        }
        assertThat(module().consecutiveFailures()).isEqualTo(5);
        assertThat(module().health()).isEqualTo("DOWN");

        // by now the breaker is open: further pulls do not touch the endpoint at all
        int before = hits.get();
        SyncResult r = puller.pull(code);
        assertThat(r.errors().get(0)).contains("circuit open");
        assertThat(hits.get()).isEqualTo(before);

        // the audit trail records the start of the streak and the move to DOWN, not every attempt
        assertThat(auditRows("MENU_MODULE_MANIFEST_FAILED")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM menu_module_manifests h JOIN menu_modules m ON m.id=h.module_id WHERE m.code=? AND h.outcome='FAILED'",
                Integer.class, code)).isGreaterThanOrEqualTo(5);                       // but every attempt is in the history
    }

    @Test
    void theSchedulerPullsDueModulesAndSurvivesFailures() {
        // other tests' modules must not be pulled by this tick
        jdbc.update("UPDATE menu_modules SET next_attempt_at = now() + interval '1 day' WHERE code <> ?", code);

        scheduler.tick();                         // approve made it due at once
        assertThat(label()).isEqualTo("A");
        assertThat(module().lastSyncedAt()).isNotNull();

        jdbc.update("UPDATE menu_modules SET next_attempt_at = now() - interval '1 second' WHERE code = ?", code);
        status.set(500);
        scheduler.tick();                         // must not throw
        assertThat(module().consecutiveFailures()).isEqualTo(1);

        // not due again until its backoff passes
        int before = hits.get();
        scheduler.tick();
        assertThat(hits.get()).isEqualTo(before);
    }
}
