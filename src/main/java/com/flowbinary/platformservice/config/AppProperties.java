package com.flowbinary.platformservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String version = "0.0.1-SNAPSHOT";
    private KafkaProperties       kafka       = new KafkaProperties();
    private SecurityProperties    security    = new SecurityProperties();
    private PlatformProperties    platform    = new PlatformProperties();
    private EnvironmentProperties environment = new EnvironmentProperties();
    private StorageConfigProperties storage   = new StorageConfigProperties();
    private MenuProperties        menu        = new MenuProperties();
    private NotificationServiceProperties notificationService = new NotificationServiceProperties();

    @Getter
    @Setter
    public static class MenuProperties {
        /** Maximum number of menu levels (root = level 1). Enforced on every write. */
        private int maxDepth = 4;
        private CacheProperties cache = new CacheProperties();
        private ModuleProperties modules = new ModuleProperties();

        @Getter
        @Setter
        public static class ModuleProperties {
            /** Largest manifest accepted, in bytes. */
            private long maxManifestBytes = 262_144;
            /** How often an ACTIVE module with a manifest URL is pulled. */
            private java.time.Duration pullInterval = java.time.Duration.ofHours(1);
            /** First retry delay after a failed pull; doubles per consecutive failure, capped at pullInterval. */
            private java.time.Duration backoffBase = java.time.Duration.ofMinutes(1);
            private java.time.Duration connectTimeout = java.time.Duration.ofSeconds(2);
            private java.time.Duration readTimeout = java.time.Duration.ofSeconds(3);
            /** Consecutive failures after which a module's health is DOWN. */
            private int failuresBeforeDown = 5;
            /** Manifest URLs resolving to loopback / private addresses. Off by default (SSRF); dev only. */
            private boolean allowPrivateHosts = false;
            /** Plain http manifest URLs. Off by default; dev only. */
            private boolean allowInsecureHttp = false;
            /**
             * Codes a module may not register: the platform's own menu areas (see
             * Docs/design/menu-catalogue.md). Module items are {@code <module>.<key>},
             * so a module named {@code billing} would collide with {@code billing.invoices}.
             */
            private java.util.List<String> reservedCodes = java.util.List.of(
                    "admin", "ai_review", "ai_studio", "billing", "charting", "claims", "coding", "compliance",
                    "dashboard", "data", "directory", "documents", "encounters", "header", "help", "home", "inbox",
                    "kiosk", "lab", "me", "monitoring", "network", "notifications", "orders", "pa", "patients",
                    "pharmacy", "pp", "quick", "referral", "referrals", "reports", "results", "schedule",
                    "settings", "staff", "telehealth", "user", "workflows");
        }

        @Getter
        @Setter
        public static class CacheProperties {
            /** Safety net: a compiled catalogue is rebuilt at least this often even if no version moved. */
            private java.time.Duration catalogueTtl = java.time.Duration.ofMinutes(5);
            /** How often an instance re-reads the version counters. Bounds how stale a menu can be. */
            private java.time.Duration versionCheck = java.time.Duration.ofSeconds(2);
            private long maxEntries = 500;
        }
    }

    @Getter
    @Setter
    public static class NotificationServiceProperties {
        private String baseUrl = "http://localhost:3002";
        private String internalApiKey = "";
        private int connectTimeoutMs = 3000;
        private int readTimeoutMs = 5000;
    }

    @Getter
    @Setter
    public static class StorageConfigProperties {
        private String  defaultBackend         = "LOCAL";
        private long    defaultQuotaMb         = 512L;
        private String  localBasePath          = "/tmp/flowcore/uploads";
        private long    presignedUrlTtlSeconds = 3600L;
        private boolean s3PublicBucket         = false;
    }

    @Getter
    @Setter
    public static class PlatformProperties {
        private String name             = "FlowCore";
        private String supportUrl       = "https://support.flowcore.io";
        private String documentationUrl = "https://docs.flowcore.io";
        private String domain           = "flowcore.io";
        private String logoUrl;
        private String faviconUrl;
    }

    @Getter
    @Setter
    public static class EnvironmentProperties {
        private String name        = "development";
        private String region      = "us-east-1";
        private String deployment  = "local";
        private String buildNumber = "local";
    }

    @Getter
    @Setter
    public static class SecurityProperties {
        /** Keycloak client IDs allowed to call internal-only endpoints (matched against JWT azp claim). */
        private List<String> allowedClients = List.of();
    }

    @Getter
    @Setter
    public static class KafkaProperties {
        private TopicProperties topics = new TopicProperties();
        private String schemaRegistryUrl = "http://localhost:8081";

        @Getter
        @Setter
        public static class TopicProperties {
            private String tenantEvents        = "platform.tenant.events";
            private String featureEvents       = "platform.feature.events";
            private String menuEvents          = "platform.menu.events";
            private String menuCatalogEvents   = "platform.menu.catalog.events";
            private String authzEvents         = "platform.authz.events";
            private String userEvents          = "platform.user.events";
            private String notificationEvents  = "platform.notification.events";
            private String dlqSuffix           = ".DLT";
        }
    }
}
