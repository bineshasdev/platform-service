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
            private String userEvents          = "platform.user.events";
            private String notificationEvents  = "platform.notification.events";
            private String dlqSuffix           = ".DLT";
        }
    }
}
