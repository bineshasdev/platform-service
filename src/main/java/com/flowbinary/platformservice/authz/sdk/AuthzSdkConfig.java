package com.flowbinary.platformservice.authz.sdk;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.annotation.AnnotationTemplateExpressionDefaults;

/** Enables {@code {value}} placeholders in meta-annotations such as {@link RequiresPrivilege}. */
@Configuration
public class AuthzSdkConfig {

    @Bean
    static AnnotationTemplateExpressionDefaults annotationTemplateExpressionDefaults() {
        return new AnnotationTemplateExpressionDefaults();
    }
}
