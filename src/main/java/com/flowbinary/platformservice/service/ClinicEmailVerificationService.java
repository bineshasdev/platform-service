package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.authz.service.PrimaryRoles;
import com.flowbinary.platformservice.dto.otp.SendOtpResponse;
import com.flowbinary.platformservice.dto.registration.EmailVerificationResponse;
import com.flowbinary.platformservice.exception.InvalidOtpException;
import com.flowbinary.platformservice.exception.OtpAttemptsExceededException;
import com.flowbinary.platformservice.exception.OtpExpiredException;
import com.flowbinary.platformservice.model.OtpChannel;
import com.flowbinary.platformservice.model.Tenant;
import com.flowbinary.platformservice.model.TenantUser;
import com.flowbinary.platformservice.repository.ClinicRepository;
import com.flowbinary.platformservice.repository.TenantRepository;
import com.flowbinary.platformservice.repository.TenantUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Confirms that the clinic admin owns the email address given at signup, using
 * the platform OTP flow ({@link OtpService}).
 *
 * <p>The code is sent server-side, after the registration transaction commits, so a
 * mail failure never fails a valid signup — the admin just uses resend. Callers
 * verify by {@code tenantId + code}; the OTP verification token is never exposed.
 * Until verified, the tenant can work normally but cannot submit for review
 * ({@link #requireVerified}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClinicEmailVerificationService {

    static final String PURPOSE = "CLINIC_EMAIL_VERIFY";

    private final PrimaryRoles primaryRoles;
    private final TenantRepository     tenantRepository;
    private final TenantUserRepository tenantUserRepository;
    private final ClinicRepository     clinicRepository;
    private final OtpService           otpService;
    private final KeycloakUserService  keycloak;

    /** Published by registration; handled once that transaction has committed. */
    public record Requested(UUID tenantId) {}

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRequested(Requested event) {
        try {
            sendCode(getTenant(event.tenantId()));
        } catch (RuntimeException ex) {
            // Not fatal — the admin can request a new code.
            log.warn("Could not send signup verification email for tenant {}: {}", event.tenantId(), ex.getMessage());
        }
    }

    @Transactional
    public EmailVerificationResponse resend(UUID tenantId) {
        Tenant tenant = getTenant(tenantId);
        if (tenant.isEmailVerified()) {
            return alreadyVerified(tenant);
        }
        return sent(tenant, sendCode(tenant));
    }

    @Transactional(noRollbackFor = {
            InvalidOtpException.class, OtpExpiredException.class, OtpAttemptsExceededException.class})
    public EmailVerificationResponse verify(UUID tenantId, String otp) {
        Tenant tenant = getTenant(tenantId);
        if (tenant.isEmailVerified()) {
            return alreadyVerified(tenant);
        }

        otpService.verifyLatest(OtpChannel.EMAIL, tenant.getEmail(), PURPOSE, otp);

        tenant.setEmailVerifiedAt(Instant.now());
        tenantRepository.save(tenant);

        adminUser(tenant).ifPresent(admin -> {
            try {
                keycloak.setEmailVerified(admin.getAuthUserId().toString(), true);
            } catch (RuntimeException ex) {
                // The platform record is the source of truth for gating; don't undo it for a Keycloak blip.
                log.warn("Tenant {} email verified but Keycloak flag not updated: {}", tenantId, ex.getMessage());
            }
        });

        log.info("Clinic email verified: tenant={}", tenantId);
        return EmailVerificationResponse.builder()
                .emailVerified(true)
                .email(mask(tenant.getEmail()))
                .message("Email verified")
                .build();
    }

    /**
     * Corrects a mistyped signup address. Only allowed while unverified, and sends a
     * fresh code to the new address.
     */
    @Transactional
    public EmailVerificationResponse changeEmail(UUID tenantId, String rawEmail) {
        Tenant tenant = getTenant(tenantId);
        if (tenant.isEmailVerified()) {
            throw new IllegalArgumentException("Email is already verified and can't be changed here");
        }
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        if (!email.equalsIgnoreCase(tenant.getEmail())) {
            if (tenantRepository.existsByEmail(email)) {
                throw new IllegalArgumentException("Email already registered: " + email);
            }
            TenantUser admin = adminUser(tenant).orElseThrow(
                    () -> new IllegalStateException("No admin user for tenant " + tenantId));
            keycloak.updateEmail(admin.getAuthUserId().toString(), email);

            tenant.setEmail(email);
            tenantRepository.save(tenant);
            admin.setEmail(email);
            tenantUserRepository.save(admin);
            clinicRepository.findByTenantId(tenantId).ifPresent(clinic -> {
                clinic.setEmail(email);
                clinicRepository.save(clinic);
            });
        }
        return sent(tenant, sendCode(tenant));
    }

    /** Gate for sensitive steps (e.g. submitting for platform review). */
    public static void requireVerified(Tenant tenant) {
        if (!tenant.isEmailVerified()) {
            throw new IllegalArgumentException("Verify your email address before submitting for review");
        }
    }

    // ── internals ─────────────────────────────────────────────────────────

    private SendOtpResponse sendCode(Tenant tenant) {
        TenantUser admin = adminUser(tenant).orElse(null);
        return otpService.sendOtp(OtpChannel.EMAIL, tenant.getEmail(), PURPOSE, tenant, admin);
    }

    private java.util.Optional<TenantUser> adminUser(Tenant tenant) {
        return primaryRoles.adminSubs(tenant.getId()).stream()
                .map(sub -> tenantUserRepository.findByTenantIdAndAuthUserId(tenant.getId(), sub))
                .flatMap(java.util.Optional::stream)
                .findFirst();
    }

    private EmailVerificationResponse sent(Tenant tenant, SendOtpResponse sent) {
        return EmailVerificationResponse.builder()
                .emailVerified(false)
                .email(mask(tenant.getEmail()))
                .expiresInSeconds(sent.getExpiresInSeconds())
                .message("Verification code sent")
                .build();
    }

    private EmailVerificationResponse alreadyVerified(Tenant tenant) {
        return EmailVerificationResponse.builder()
                .emailVerified(true)
                .email(mask(tenant.getEmail()))
                .message("Email already verified")
                .build();
    }

    private Tenant getTenant(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new NoSuchElementException("Tenant not found: " + tenantId));
    }

    private static String mask(String email) {
        int at = email.indexOf('@');
        if (at < 0) return email;
        String local = email.substring(0, at);
        String masked = local.length() <= 2 ? local : local.charAt(0) + "***" + local.charAt(local.length() - 1);
        return masked + email.substring(at);
    }
}
