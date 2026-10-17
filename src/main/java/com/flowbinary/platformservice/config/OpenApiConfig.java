package com.flowbinary.platformservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    @Value("${app.version:0.0.1-SNAPSHOT}")
    private String appVersion;

    @Bean
    public OpenAPI platformServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("FlowCore Platform Service API")
                        .description("""
                                Multi-tenant SaaS platform service.
                                Manages tenants, plans, features, menu navigation,
                                settings, themes, and reference data.
                                """)
                        .version(appVersion)
                        .contact(new Contact()
                                .name("FlowBinary")
                                .email("dev@flowbinary.com"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://flowbinary.com")))
                .externalDocs(new ExternalDocumentation()
                        .description("Architecture Docs")
                        .url("https://docs.flowbinary.com"))
                .servers(List.of(
                        new Server().url("http://localhost:8083").description("Local Dev"),
                        new Server().url("https://api.flowbinary.com").description("Production")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH,
                                new SecurityScheme()
                                        .name(BEARER_AUTH)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT token from Supabase / Keycloak")));
    }
}
