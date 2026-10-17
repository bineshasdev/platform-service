package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.dto.registration.CredentialRequest;
import com.flowbinary.platformservice.dto.registration.CredentialResponse;
import com.flowbinary.platformservice.dto.registration.Decision;
import com.flowbinary.platformservice.dto.registration.VerificationDecisionRequest;
import com.flowbinary.platformservice.model.CredentialStatus;
import com.flowbinary.platformservice.model.Tenant;
import com.flowbinary.platformservice.model.TenantCredential;
import com.flowbinary.platformservice.model.TenantFile;
import com.flowbinary.platformservice.repository.TenantCredentialRepository;
import com.flowbinary.platformservice.repository.TenantFileRepository;
import com.flowbinary.platformservice.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Manages a tenant's legal / licensing credentials ({@link TenantCredential}) —
 * capture by the tenant and per-document review by a platform operator.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantCredentialService {

    private final TenantRepository           tenantRepository;
    private final TenantCredentialRepository credentialRepository;
    private final TenantFileRepository       tenantFileRepository;

    @Transactional
    public CredentialResponse add(UUID tenantId, CredentialRequest req) {
        Tenant tenant = getTenant(tenantId);

        TenantFile document = null;
        if (req.getDocumentFileId() != null) {
            document = tenantFileRepository.findByIdAndTenantId(req.getDocumentFileId(), tenantId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "documentFileId does not belong to this tenant: " + req.getDocumentFileId()));
        }

        TenantCredential credential = TenantCredential.builder()
                .tenant(tenant)
                .type(req.getType())
                .country(StringUtils.hasText(req.getCountry()) ? req.getCountry() : tenant.getCountry())
                .credentialNumber(req.getCredentialNumber())
                .issuingAuthority(req.getIssuingAuthority())
                .holderName(req.getHolderName())
                .issueDate(req.getIssueDate())
                .expiryDate(req.getExpiryDate())
                .status(CredentialStatus.SUBMITTED)
                .documentFile(document)
                .attributes(req.getAttributes())
                .build();

        credential = credentialRepository.save(credential);
        log.debug("Credential {} ({}) added for tenant {}", credential.getId(), req.getType(), tenant.getSlug());
        return toResponse(credential);
    }

    @Transactional(readOnly = true)
    public List<CredentialResponse> list(UUID tenantId) {
        getTenant(tenantId);
        return credentialRepository.findByTenantId(tenantId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void delete(UUID tenantId, UUID credentialId) {
        TenantCredential credential = credentialRepository.findByIdAndTenantId(credentialId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Credential not found: " + credentialId));
        if (credential.getStatus() == CredentialStatus.VERIFIED) {
            throw new IllegalArgumentException("Cannot remove a verified credential");
        }
        credentialRepository.delete(credential);
    }

    /** Platform-operator action: approve or reject a single credential. */
    @Transactional
    public CredentialResponse review(UUID tenantId, UUID credentialId, VerificationDecisionRequest decision) {
        getTenant(tenantId);
        TenantCredential credential = credentialRepository.findByIdAndTenantId(credentialId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Credential not found: " + credentialId));

        if (decision.getDecision() == Decision.REJECT && !StringUtils.hasText(decision.getNotes())) {
            throw new IllegalArgumentException("notes are required when rejecting a credential");
        }

        credential.setStatus(decision.getDecision() == Decision.APPROVE
                ? CredentialStatus.VERIFIED : CredentialStatus.REJECTED);
        credential.setReviewedBy(currentUserId());
        credential.setReviewedAt(Instant.now());
        credential.setReviewNotes(decision.getNotes());
        credential = credentialRepository.save(credential);

        log.info("Credential {} for tenant {} -> {}", credentialId, tenantId, credential.getStatus());
        return toResponse(credential);
    }

    // ── internal ──────────────────────────────────────────────────────────

    private Tenant getTenant(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new NoSuchElementException("Tenant not found: " + tenantId));
    }

    private CredentialResponse toResponse(TenantCredential c) {
        return CredentialResponse.builder()
                .id(c.getId())
                .tenantId(c.getTenant().getId())
                .type(c.getType())
                .country(c.getCountry())
                .credentialNumber(c.getCredentialNumber())
                .issuingAuthority(c.getIssuingAuthority())
                .holderName(c.getHolderName())
                .issueDate(c.getIssueDate())
                .expiryDate(c.getExpiryDate())
                .status(c.getStatus())
                .documentFileId(c.getDocumentFile() != null ? c.getDocumentFile().getId() : null)
                .reviewedBy(c.getReviewedBy())
                .reviewedAt(c.getReviewedAt())
                .reviewNotes(c.getReviewNotes())
                .attributes(c.getAttributes())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }

    private static UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt && jwt.getToken().getSubject() != null) {
            try {
                return UUID.fromString(jwt.getToken().getSubject());
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }
}
