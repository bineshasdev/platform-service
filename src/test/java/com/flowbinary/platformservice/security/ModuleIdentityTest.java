package com.flowbinary.platformservice.security;

import com.flowbinary.platformservice.model.MenuModule;
import com.flowbinary.platformservice.repository.MenuModuleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModuleIdentityTest {

    private final MenuModuleRepository repo = mock(MenuModuleRepository.class);
    private final ModuleIdentity identity = new ModuleIdentity(repo);

    ModuleIdentityTest() {
        when(repo.findByCode("billing")).thenReturn(Optional.of(MenuModule.builder().code("billing").ownerClientId("billing-svc").build()));
        when(repo.findByCode("ghost")).thenReturn(Optional.empty());
    }

    private static JwtAuthenticationToken token(Map<String, Object> claims) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "none").subject("s");
        claims.forEach(b::claim);
        return new JwtAuthenticationToken(b.build());
    }

    @Test
    void theOwningClientsServiceTokenIsAccepted() {
        var t = token(Map.of("azp", "billing-svc", "preferred_username", "service-account-billing-svc"));
        assertThat(identity.requireOwner(t, "billing")).isEqualTo("billing-svc");
        var viaClientIdClaim = token(Map.of("azp", "billing-svc", "clientId", "billing-svc"));
        assertThat(identity.requireOwner(viaClientIdClaim, "billing")).isEqualTo("billing-svc");
    }

    @Test
    void anotherClientIsRefused() {
        var t = token(Map.of("azp", "lab-svc", "preferred_username", "service-account-lab-svc"));
        assertThatThrownBy(() -> identity.requireOwner(t, "billing")).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aUserTokenOfTheSameClientIsRefused() {
        // authorization-code flow: the azp matches, but a person is behind it
        var t = token(Map.of("azp", "billing-svc", "preferred_username", "alice"));
        assertThatThrownBy(() -> identity.requireOwner(t, "billing")).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void anUnknownModuleLooksTheSameAsAWrongClient() {
        var t = token(Map.of("azp", "billing-svc", "preferred_username", "service-account-billing-svc"));
        assertThatThrownBy(() -> identity.requireOwner(t, "ghost"))
                .isInstanceOf(AccessDeniedException.class).hasMessage("Not permitted");
    }

    @Test
    void missingAzpAndNonJwtAreRefused() {
        assertThatThrownBy(() -> identity.requireOwner(token(Map.of("preferred_username", "service-account-x")), "billing"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> identity.requireOwner(new UsernamePasswordAuthenticationToken("u", "p"), "billing"))
                .isInstanceOf(AccessDeniedException.class);
    }
}
