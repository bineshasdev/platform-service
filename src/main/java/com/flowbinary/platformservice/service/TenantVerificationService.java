package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.dto.registration.Decision;
import com.flowbinary.platformservice.dto.registration.RegistrationStatusResponse;
import com.flowbinary.platformservice.dto.registration.VerificationDecisionRequest;
import com.flowbinary.platformservice.event.TenantVerificationChangedEvent;
import com.flowbinary.platformservice.messaging.PlatformEventPublisher;
import com.flowbinary.platformservice.model.CredentialStatus;
import com.flowbinary.platformservice.model.Tenant;
import com.flowbinary.platformservice.model.VerificationStatus;
import com.flowbinary.platformservice.repository.TenantCredentialRepository;
import com.flowbinary.platformservice.repository.TenantLegalProfileRepository;
import com.flowbinary.platformservice.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Drives a tenant's verification lifecycle:
 * {@code PENDING → IN_REVIEW → VERIFIED / REJECTED}.
 *
 * <p>A tenant is exposed in public search / directory ({@code listed = true})
 * only on approval, at which point a {@link TenantVerificationChangedEvent} is
 * published for the search / directory service to (de)index it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantVerificationService {

    private final TenantRepository             tenantRepository;
    private final TenantLegalProfileRepository legalProfileRepository;
    private final TenantCredentialRepository   credentialRepository;
    private final PlatformEventPublisher       eventPublisher;

    /** Tenant-admin action: hand the legal profile + credentials to platform review. */
    @Transactional
    public RegistrationStatusResponse submitForReview(UUID tenantId) {
        Tenant tenant = getTenant(tenantId);

        if (tenant.getVerificationStatus() == VerificationStatus.VERIFIED) {
            throw new IllegalArgumentException("Tenant is already verified");
        }
        ClinicEmailVerificationService.requireVerified(tenant);
        if (!legalProfileRepository.existsByTenantId(tenantId)) {
            throw new IllegalArgumentException("Add a legal profile before submitting for review");
        }
        if (credentialRepository.countByTenantId(tenantId) == 0) {
            throw new IllegalArgumentException("Add at least one licence / credential before submitting for review");
        }

        transition(tenant, VerificationStatus.IN_REVIEW, false, "submit-for-review");
        return buildStatus(tenant);
    }

    /**
     * Platform-operator action: the final decision. APPROVE lists the tenant
     * publicly; REJECT sends it back for correction.
     */
    @Transactional
    public RegistrationStatusResponse decide(UUID tenantId, VerificationDecisionRequest decision) {
        Tenant tenant = getTenant(tenantId);

        if (decision.getDecision() == Decision.APPROVE) {
            long unresolved = credentialRepository.countByTenantId(tenantId)
                    - credentialRepository.countByTenantIdAndStatus(tenantId, CredentialStatus.VERIFIED);
            if (unresolved > 0) {
                throw new IllegalArgumentException(
                        "All credentials must be VERIFIED before approving the tenant (" + unresolved + " outstanding)");
            }
            tenant.setVerifiedAt(Instant.now());
            transition(tenant, VerificationStatus.VERIFIED, true, "verification-approved");
        } else {
            if (!StringUtils.hasText(decision.getNotes())) {
                throw new IllegalArgumentException("notes are required when rejecting a tenant");
            }
            tenant.setVerifiedAt(null);
            transition(tenant, VerificationStatus.REJECTED, false, "verification-rejected");
        }
        return buildStatus(tenant);
    }

    @Transactional(readOnly = true)
    public RegistrationStatusResponse getStatus(UUID tenantId) {
        return buildStatus(getTenant(tenantId));
    }

    // ── internal ──────────────────────────────────────────────────────────

    private void transition(Tenant tenant, VerificationStatus target, boolean listed, String reason) {
        VerificationStatus previous = tenant.getVerificationStatus();
        tenant.setVerificationStatus(target);
        tenant.setListed(listed);
        tenantRepository.save(tenant);

        eventPublisher.publishTenantVerificationChanged(TenantVerificationChangedEvent.builder()
                .tenantId(tenant.getId())
                .tenantName(tenant.getName())
                .slug(tenant.getSlug())
                .type(tenant.getType() != null ? tenant.getType().name() : null)
                .previousStatus(previous != null ? previous.name() : null)
                .newStatus(target.name())
                .listed(listed)
                .changedBy(reason)
                .build());

        log.info("Tenant {} verification {} -> {} (listed={})", tenant.getSlug(), previous, target, listed);
    }

    private RegistrationStatusResponse buildStatus(Tenant tenant) {
        UUID tenantId = tenant.getId();
        boolean legal = legalProfileRepository.existsByTenantId(tenantId);
        long total    = credentialRepository.countByTenantId(tenantId);
        long verified = credentialRepository.countByTenantIdAndStatus(tenantId, CredentialStatus.VERIFIED);

        List<String> outstanding = new ArrayList<>();
        if (!tenant.isEmailVerified())     outstanding.add("Verify your email address");
        if (!legal)                        outstanding.add("Provide the legal profile");
        if (total == 0)                    outstanding.add("Upload at least one licence / credential");
        if (total > 0 && verified < total) outstanding.add("Awaiting review of " + (total - verified) + " credential(s)");
        if (tenant.getVerificationStatus() == VerificationStatus.PENDING && legal && total > 0)
            outstanding.add("Submit for review");
        if (tenant.getVerificationStatus() == VerificationStatus.REJECTED)
            outstanding.add("Address reviewer feedback and resubmit");

        return RegistrationStatusResponse.builder()
                .tenantId(tenantId)
                .slug(tenant.getSlug())
                .type(tenant.getType())
                .verificationStatus(tenant.getVerificationStatus())
                .listed(tenant.isListed())
                .emailVerified(tenant.isEmailVerified())
                .verifiedAt(tenant.getVerifiedAt())
                .legalProfileProvided(legal)
                .credentialsSubmitted((int) total)
                .credentialsVerified((int) verified)
                .outstanding(outstanding)
                .build();
    }

    private Tenant getTenant(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new NoSuchElementException("Tenant not found: " + tenantId));
    }
}
