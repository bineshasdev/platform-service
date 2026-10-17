package com.flowbinary.platformservice.controller;

import com.flowbinary.platformservice.dto.registration.CredentialRequest;
import com.flowbinary.platformservice.dto.registration.CredentialResponse;
import com.flowbinary.platformservice.dto.registration.VerificationDecisionRequest;
import com.flowbinary.platformservice.service.TenantCredentialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * A tenant's legal / licensing credentials — capture by the tenant, and
 * per-document review by a platform operator.
 */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/credentials")
@RequiredArgsConstructor
@Tag(name = "Tenant credentials", description = "Licences and registrations")
public class TenantCredentialController {

    private final TenantCredentialService credentialService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Attach a licence / credential",
            description = "Upload the certificate first via POST /api/v1/files (fileType DOCUMENT), "
                    + "then pass its id as documentFileId.")
    CredentialResponse add(@PathVariable UUID tenantId,
                           @Valid @RequestBody CredentialRequest request) {
        return credentialService.add(tenantId, request);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List the tenant's credentials")
    List<CredentialResponse> list(@PathVariable UUID tenantId) {
        return credentialService.list(tenantId);
    }

    @DeleteMapping("/{credentialId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Remove a not-yet-verified credential")
    void delete(@PathVariable UUID tenantId, @PathVariable UUID credentialId) {
        credentialService.delete(tenantId, credentialId);
    }

    @PostMapping("/{credentialId}/review")
    @PreAuthorize("hasAuthority('ROLE_SUPERADMIN')")
    @Operation(summary = "Approve or reject a single credential (platform operator)")
    CredentialResponse review(@PathVariable UUID tenantId,
                              @PathVariable UUID credentialId,
                              @Valid @RequestBody VerificationDecisionRequest request) {
        return credentialService.review(tenantId, credentialId, request);
    }
}
