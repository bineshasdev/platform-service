package com.flowbinary.platformservice.controller;

import com.flowbinary.platformservice.authz.sdk.Authz;
import com.flowbinary.platformservice.authz.sdk.AuthzSdkConfig;
import com.flowbinary.platformservice.config.SecurityConfig;
import com.flowbinary.platformservice.config.StorageProperties;
import com.flowbinary.platformservice.dto.user.UserResponse;
import com.flowbinary.platformservice.security.TenantContextResolver;
import com.flowbinary.platformservice.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every protected route of the controllers changed in phase 3, through the real security configuration,
 * the real {@code @RequiresPrivilege} annotation and real Spring method security. The decision bean is
 * stubbed, so these tests prove that each route asks the right question and obeys the answer; what the
 * answer is, is covered by {@code AuthzIT}.
 */
@WebMvcTest(controllers = {UserController.class, TenantLegalProfileController.class, TenantCredentialController.class,
        TenantVerificationController.class, StorageConfigController.class, PlanController.class,
        FileStorageController.class, BootstrapController.class, MenuController.class})
@Import({SecurityConfig.class, AuthzSdkConfig.class})
class ControllerSecurityTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    @Autowired MockMvc mvc;

    @MockitoBean(name = "authz") Authz authz;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean UserService userService;
    @MockitoBean TenantContextResolver tenantContext;
    @MockitoBean TenantLegalProfileService legalProfiles;
    @MockitoBean TenantCredentialService credentials;
    @MockitoBean TenantVerificationService verification;
    @MockitoBean FileStorageService files;
    @MockitoBean StorageProperties storageProperties;
    @MockitoBean PlanService plans;
    @MockitoBean BootstrapService bootstrap;
    @MockitoBean MenuQueryService menus;

    private final RequestPostProcessor user = jwt().jwt(j -> j.subject(UUID.randomUUID().toString()));

    @BeforeEach
    void denyEverythingByDefault() {
        when(authz.has(any(), any())).thenReturn(false);
        when(authz.member(any())).thenReturn(false);
        when(authz.hasInTenant(any(), any(), any())).thenReturn(false);
        when(userService.createUser(any(), any())).thenReturn(UserResponse.builder().build());
    }

    private void grant(String privilege) {
        when(authz.has(any(), eq(privilege))).thenReturn(true);
    }

    // ── no token ───────────────────────────────────────────────────────────

    @Test
    void anonymousCallersGetNoAccess() throws Exception {
        mvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/users/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/tenants/" + TENANT + "/legal-profile")).andExpect(status().isUnauthorized());
    }

    // ── users ──────────────────────────────────────────────────────────────

    @Test
    void listingUsersNeedsMemberView() throws Exception {
        mvc.perform(get("/api/v1/users").with(user)).andExpect(status().isForbidden());
        grant("platform.member.view");
        mvc.perform(get("/api/v1/users").with(user)).andExpect(status().isOk());
    }

    @Test
    void creatingChangingAndDeletingUsersEachNeedTheirOwnPrivilege() throws Exception {
        String create = """
                {"firstName":"A","lastName":"B","email":"a@b.test","temporaryPassword":"x"}""";
        mvc.perform(post("/api/v1/users").with(user).contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isForbidden());
        grant("platform.member.invite");
        mvc.perform(post("/api/v1/users").with(user).contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isCreated());

        UUID id = UUID.randomUUID();
        mvc.perform(patch("/api/v1/users/" + id).with(user).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/users/" + id).with(user)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/users/" + id + "/reset-credentials").with(user).contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\":\"longenough1\",\"temporary\":true}")).andExpect(status().isForbidden());

        grant("platform.member.manage");
        mvc.perform(patch("/api/v1/users/" + id).with(user).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/users/" + id).with(user)).andExpect(status().isNoContent());
    }

    @Test
    void inviteAloneDoesNotAllowChoosingARole() throws Exception {
        String withRole = """
                {"firstName":"A","lastName":"B","email":"a@b.test","temporaryPassword":"x","primaryRoleId":"%s"}""".formatted(UUID.randomUUID());
        grant("platform.member.invite");
        mvc.perform(post("/api/v1/users").with(user).contentType(MediaType.APPLICATION_JSON).content(withRole))
                .andExpect(status().isForbidden());
        grant("platform.member.manage");
        mvc.perform(post("/api/v1/users").with(user).contentType(MediaType.APPLICATION_JSON).content(withRole))
                .andExpect(status().isCreated());
    }

    @Test
    void ownProfileNeedsOnlyAnActiveMembership() throws Exception {
        mvc.perform(get("/api/v1/users/me").with(user)).andExpect(status().isForbidden());
        when(authz.member(any())).thenReturn(true);
        mvc.perform(get("/api/v1/users/me").with(user)).andExpect(status().isOk());
    }

    // ── a tenant named in the path ─────────────────────────────────────────

    @Test
    void tenantDataInThePathNeedsTheSettingsPrivilegeInThatSameTenant() throws Exception {
        for (String path : new String[]{"legal-profile", "credentials", "verification/status"}) {
            mvc.perform(get("/api/v1/tenants/" + OTHER + "/" + path).with(user)).andExpect(status().isForbidden());
        }
        // holding the privilege is not enough when the path names another tenant: the bean is asked about the path tenant
        when(authz.hasInTenant(any(), eq("platform.settings.manage"), eq(TENANT))).thenReturn(true);
        mvc.perform(get("/api/v1/tenants/" + OTHER + "/legal-profile").with(user)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/tenants/" + TENANT + "/legal-profile").with(user)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/tenants/" + OTHER + "/verification/submit").with(user)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/tenants/" + TENANT + "/verification/submit").with(user)).andExpect(status().isOk());
        verify(authz, atLeastOnce()).hasInTenant(any(), eq("platform.settings.manage"), eq(OTHER));
    }

    @Test
    void verificationDecisionsAreForPlatformOperatorsOnly() throws Exception {
        grant("platform.settings.manage");                                      // a tenant admin's privilege is not enough
        String decision = "{\"decision\":\"APPROVE\"}";
        mvc.perform(post("/api/v1/tenants/" + TENANT + "/verification/decision").with(user)
                .contentType(MediaType.APPLICATION_JSON).content(decision)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/tenants/" + TENANT + "/credentials/" + UUID.randomUUID() + "/review").with(user)
                .contentType(MediaType.APPLICATION_JSON).content(decision)).andExpect(status().isForbidden());
        grant("platform.tenant.verify");
        mvc.perform(post("/api/v1/tenants/" + TENANT + "/verification/decision").with(user)
                .contentType(MediaType.APPLICATION_JSON).content(decision)).andExpect(status().isOk());
    }

    // ── tenant settings and subscription ───────────────────────────────────

    @Test
    void storageConfigurationAndPlanChangesNeedTheirPrivileges() throws Exception {
        mvc.perform(get("/api/v1/storage-config").with(user)).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/storage-config").with(user).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/plans/upgrade").with(user).contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetPlan\":\"pro\"}")).andExpect(status().isForbidden());

        grant("platform.subscription.manage");
        mvc.perform(post("/api/v1/plans/upgrade").with(user).contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetPlan\":\"pro\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/storage-config").with(user)).andExpect(status().isForbidden());   // a different privilege
    }

    @Test
    void thePlanListStaysPublicToSignedInCallers() throws Exception {
        mvc.perform(get("/api/v1/plans").with(user)).andExpect(status().isOk());
    }

    // ── files, bootstrap, menu ─────────────────────────────────────────────

    @Test
    void filesBootstrapAndMenuNeedAnActiveMembership() throws Exception {
        UUID file = UUID.randomUUID();
        mvc.perform(get("/api/v1/files/" + file).with(user)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/bootstrap").with(user)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/menus/me?application=PROVIDER_CONSOLE").with(user)).andExpect(status().isForbidden());

        when(authz.member(any())).thenReturn(true);
        mvc.perform(get("/api/v1/menus/me?application=PROVIDER_CONSOLE").with(user)).andExpect(status().isOk());
        // being a member does not allow deleting files
        mvc.perform(delete("/api/v1/files/" + file).with(user)).andExpect(status().isForbidden());
        grant("platform.document.delete");
        mvc.perform(delete("/api/v1/files/" + file).with(user)).andExpect(status().isNoContent());
    }
}
