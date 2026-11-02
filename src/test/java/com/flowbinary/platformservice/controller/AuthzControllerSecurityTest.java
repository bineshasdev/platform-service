package com.flowbinary.platformservice.controller;

import com.flowbinary.platformservice.authz.sdk.Authz;
import com.flowbinary.platformservice.authz.sdk.AuthzSdkConfig;
import com.flowbinary.platformservice.authz.service.*;
import com.flowbinary.platformservice.authz.web.AuthzController;
import com.flowbinary.platformservice.config.SecurityConfig;
import com.flowbinary.platformservice.repository.TenantRepository;
import com.flowbinary.platformservice.repository.TenantUserRepository;
import com.flowbinary.platformservice.security.JwtClientValidator;
import com.flowbinary.platformservice.security.TenantContextResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The operator and service-facing routes added in phase 4, through the real security configuration. */
@WebMvcTest(controllers = {AuthzController.class, com.flowbinary.platformservice.controller.AuthzManifestController.class})
@Import({SecurityConfig.class, AuthzSdkConfig.class})
class AuthzControllerSecurityTest {

    @Autowired MockMvc mvc;

    @MockitoBean(name = "authz") Authz authz;
    @MockitoBean(name = "jwtClientValidator") JwtClientValidator clientValidator;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean TenantContextResolver tenantContext;
    @MockitoBean TenantRepository tenants;
    @MockitoBean TenantUserRepository tenantUsers;
    @MockitoBean EffectivePermissionService permissions;
    @MockitoBean RoleAdminService roleAdmin;
    @MockitoBean MembershipService memberships;
    @MockitoBean PlatformMembershipService platformMembers;
    @MockitoBean TemplateProposalService proposals;
    @MockitoBean AuthzServiceIdentity identity;
    @MockitoBean AuthzManifestService manifests;

    private final RequestPostProcessor user = jwt().jwt(j -> j.subject(UUID.randomUUID().toString()));

    @BeforeEach
    void denyByDefault() {
        when(authz.has(any(), any())).thenReturn(false);
        when(clientValidator.isAllowed(any())).thenReturn(false);
    }

    private void grant(String privilege) {
        when(authz.has(any(), eq(privilege))).thenReturn(true);
    }

    @Test
    void reviewingServiceProposalsIsForTemplateManagersOnly() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(get("/api/v1/authz/services").with(user)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/authz/proposals").with(user)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/authz/proposals/" + id + "/approve").with(user)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/authz/proposals/" + id + "/reject").with(user).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());

        grant("platform.role.manage");                               // a tenant admin's privilege does not help
        mvc.perform(get("/api/v1/authz/proposals").with(user)).andExpect(status().isForbidden());

        grant("platform.template.manage");
        mvc.perform(get("/api/v1/authz/services").with(user)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/authz/proposals").with(user)).andExpect(status().isOk());
    }

    @Test
    void operatorsAreManagedOnlyWithTheOperatorPrivilege() throws Exception {
        UUID sub = UUID.randomUUID();
        mvc.perform(get("/api/v1/authz/platform/members").with(user)).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/authz/platform/members/" + sub).with(user).contentType(MediaType.APPLICATION_JSON)
                .content("{\"roles\":[\"super_admin\"]}")).andExpect(status().isForbidden());
        grant("platform.template.manage");
        mvc.perform(get("/api/v1/authz/platform/members").with(user)).andExpect(status().isForbidden());   // managing templates is not managing operators
        grant("platform.operator.manage");
        mvc.perform(get("/api/v1/authz/platform/members").with(user)).andExpect(status().isOk());
    }

    @Test
    void roleAndMemberAdministrationNeedTheirPrivileges() throws Exception {
        mvc.perform(get("/api/v1/authz/roles").with(user)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/authz/memberships").with(user)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/authz/templates").with(user)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/authz/privileges").with(user)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/authz/memberships/" + UUID.randomUUID() + "/explain?privilege=x.y.z").with(user)).andExpect(status().isForbidden());
    }

    @Test
    void permissionLookupsForOtherServicesNeedAnAllowedClient() throws Exception {
        UUID sub = UUID.randomUUID();
        mvc.perform(get("/api/v1/authz/principals/" + sub + "/platform/permissions").with(user)).andExpect(status().isForbidden());
        when(clientValidator.isAllowed(any())).thenReturn(true);
        when(permissions.forPlatform(any())).thenReturn(
                PermissionSet.denied(null, sub, null, PermissionSet.Status.NO_MEMBERSHIP, java.time.Instant.now()));
        mvc.perform(get("/api/v1/authz/principals/" + sub + "/platform/permissions").with(user)).andExpect(status().isOk());
    }

    @Test
    void theManifestEndpointNeedsTheServicesOwnClient() throws Exception {
        String body = "{\"service\":\"imaging\"}";
        mvc.perform(put("/api/v1/internal/authz/manifests/imaging").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());

        when(identity.requireOwner(any(), eq("imaging"))).thenThrow(new AccessDeniedException("Not permitted"));
        mvc.perform(put("/api/v1/internal/authz/manifests/imaging").with(user).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());

        when(identity.requireOwner(any(), eq("imaging"))).thenReturn("client-imaging");
        when(manifests.accept(eq("imaging"), any(), eq("client-imaging")))
                .thenReturn(new AuthzManifestService.Result(AuthzManifestService.Outcome.ACCEPTED, java.util.List.of(), java.util.List.of(),
                        java.util.List.of(), java.util.List.of(), java.util.List.of(), 0));
        mvc.perform(put("/api/v1/internal/authz/manifests/imaging").with(user).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        when(manifests.accept(eq("imaging"), any(), any()))
                .thenReturn(new AuthzManifestService.Result(AuthzManifestService.Outcome.REJECTED, java.util.List.of("bad"), java.util.List.of(),
                        java.util.List.of(), java.util.List.of(), java.util.List.of(), 0));
        mvc.perform(put("/api/v1/internal/authz/manifests/imaging").with(user).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity());
    }
}
