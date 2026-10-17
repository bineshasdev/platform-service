package com.flowbinary.platformservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/actuator/health/**",
            "/actuator/info",
            "/actuator/prometheus",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/api/v1/tenants/resolve",
            "/api/v1/lookups",
            "/api/v1/lookups/**",
            "/api/v1/plans",
            "/api/v1/otp/send",
            "/api/v1/otp/verify",
            "/api/v1/registration/doctors",
            "/api/v1/registration/patients"
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .build();
    }

    /**
     * Maps Keycloak realm roles from {@code realm_access.roles} to Spring
     * {@code ROLE_<UPPERCASE>} granted authorities so that {@code @PreAuthorize}
     * expressions like {@code hasAnyAuthority('ROLE_SUPERADMIN','ROLE_TENANTADMIN')}
     * work correctly.
     */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess == null) return List.of();

            Object rolesObj = realmAccess.get("roles");
            if (!(rolesObj instanceof Collection<?> roles)) return List.of();

            return roles.stream()
                    .filter(String.class::isInstance)
                    .map(r -> new SimpleGrantedAuthority("ROLE_" + ((String) r).toUpperCase()))
                    .collect(Collectors.toList());
        });
        return converter;
    }
}
