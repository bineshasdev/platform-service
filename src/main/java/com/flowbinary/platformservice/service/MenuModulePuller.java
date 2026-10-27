package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.config.AppProperties;
import com.flowbinary.platformservice.exception.MenuConflictException;
import com.flowbinary.platformservice.model.MenuModule;
import com.flowbinary.platformservice.model.ModuleStatus;
import com.flowbinary.platformservice.repository.MenuModuleRepository;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.NoSuchElementException;

/**
 * Fetches a module's manifest from its registered URL — on an administrator's request
 * and on the slow schedule — and hands it to the same sync engine a push uses, so a
 * pulled manifest gets exactly the validation a pushed one does.
 *
 * <p>Safe by construction: the URL is re-checked on every pull ({@link ManifestUrlPolicy}),
 * redirects are refused, time and size are bounded, and each module has its own circuit
 * breaker so one broken module is skipped quickly without slowing the others.
 * Any failure here leaves the last accepted menu untouched.
 */
@Slf4j
@Component
public class MenuModulePuller {

    private final MenuModuleRepository modules;
    private final MenuModuleSyncService sync;
    private final AppProperties properties;
    private final MenuMetrics metrics;
    private final HttpClient http;
    private final CircuitBreakerRegistry breakers = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
            .slidingWindowSize(5).minimumNumberOfCalls(3).failureRateThreshold(60)
            .waitDurationInOpenState(Duration.ofSeconds(60)).build());

    public MenuModulePuller(MenuModuleRepository modules, MenuModuleSyncService sync, AppProperties properties, MenuMetrics metrics) {
        this.modules = modules;
        this.sync = sync;
        this.properties = properties;
        this.metrics = metrics;
        this.http = HttpClient.newBuilder()
                .connectTimeout(properties.getMenu().getModules().getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /** @throws NoSuchElementException unknown module; MenuConflictException if it cannot be pulled */
    public MenuModuleSyncService.SyncResult pull(String moduleCode) {
        MenuModule module = modules.findByCode(moduleCode).orElseThrow(() -> new NoSuchElementException("Module not found"));
        if (module.getStatus() != ModuleStatus.ACTIVE) throw new MenuConflictException("Module is " + module.getStatus());
        if (module.getManifestUrl() == null) throw new MenuConflictException("Module has no manifest URL");

        AppProperties.MenuProperties.ModuleProperties cfg = properties.getMenu().getModules();
        var refused = ManifestUrlPolicy.check(module.getManifestUrl(), cfg.isAllowPrivateHosts(), cfg.isAllowInsecureHttp(),
                MenuModuleRegistryService::resolve);
        if (refused.isPresent()) return failed(moduleCode, refused.get());

        CircuitBreaker breaker = breakers.circuitBreaker("menu-module-" + moduleCode);
        String body;
        try {
            body = breaker.executeCallable(() -> fetch(module.getManifestUrl(), cfg));
        } catch (CallNotPermittedException e) {
            return failed(moduleCode, "circuit open after repeated failures; will retry later");
        } catch (Exception e) {
            return failed(moduleCode, describe(e));
        }
        return sync.accept(moduleCode, body, "PULL", null);
    }

    private String fetch(String url, AppProperties.MenuProperties.ModuleProperties cfg) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(cfg.getReadTimeout())
                .header("Accept", "application/json")
                .GET().build();
        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream in = response.body()) {
            if (response.statusCode() != 200) throw new IOException("manifest endpoint answered HTTP " + response.statusCode());
            byte[] bytes = in.readNBytes((int) cfg.getMaxManifestBytes() + 1);
            if (bytes.length > cfg.getMaxManifestBytes()) throw new IOException("manifest is larger than " + cfg.getMaxManifestBytes() + " bytes");
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private MenuModuleSyncService.SyncResult failed(String moduleCode, String message) {
        sync.recordPullFailure(moduleCode, message);
        return new MenuModuleSyncService.SyncResult(moduleCode, com.flowbinary.platformservice.model.ManifestOutcome.FAILED,
                null, false, 0, 0, 0, java.util.List.of(message));
    }

    private static String describe(Exception e) {
        if (e instanceof java.net.http.HttpTimeoutException || e instanceof java.net.http.HttpConnectTimeoutException) return "timed out";
        if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        return "could not fetch manifest: " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
    }
}
