package com.flowbinary.platformservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code audiences: ${JWT_AUDIENCES:}} must mean "do not check" when the variable is unset or empty, and
 * "check these" when set. The resource-server auto-configuration validates {@code aud} exactly when the list is not empty.
 */
class AudiencePropertyTest {

    private static List<String> audiences(String value) {
        Map<String, Object> props = Map.of("spring.security.oauth2.resourceserver.jwt.audiences", value);
        return new Binder(new MapConfigurationPropertySource(props))
                .bind("spring.security.oauth2.resourceserver", OAuth2ResourceServerProperties.class)
                .get().getJwt().getAudiences();
    }

    @Test
    void anEmptyValueMeansNoAudienceCheck() {
        assertThat(audiences("")).isEmpty();
    }

    @Test
    void aValueIsTheAudienceTokensMustCarry() {
        assertThat(audiences("cliniqai-api")).containsExactly("cliniqai-api");
        assertThat(audiences("cliniqai-api,other")).containsExactly("cliniqai-api", "other");
    }
}
