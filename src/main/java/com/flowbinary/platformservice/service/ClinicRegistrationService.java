package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.domain.Clinic;
import com.flowbinary.platformservice.domain.ClinicCategory;
import com.flowbinary.platformservice.dto.registration.ClinicRegistrationRequest;
import com.flowbinary.platformservice.dto.registration.ClinicRegistrationResponse;
import com.flowbinary.platformservice.event.ClinicCreatedEvent;
import com.flowbinary.platformservice.messaging.PlatformEventPublisher;
import com.flowbinary.platformservice.model.*;
import com.flowbinary.platformservice.repository.ClinicRepository;
import com.flowbinary.platformservice.repository.PlanRepository;
import com.flowbinary.platformservice.repository.TenantRepository;
import com.flowbinary.platformservice.repository.TenantSubscriptionRepository;
import com.flowbinary.platformservice.repository.TenantUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Clinic signup — {@code POST /api/v1/registration/clinics}.
 *
 * <p>Low-friction: only a clinic name and one admin user are required. Creates
 * the Keycloak organisation + admin user, the {@link Tenant} (type
 * {@code CLINIC} or {@code PRIVATE_CLINIC}), the operational {@link Clinic} row,
 * and a free-plan subscription. The tenant starts
 * {@link VerificationStatus#PENDING} / unlisted; legal profile, credentials and
 * verification are handled by their own services / endpoints.
 *
 * <p>Separate from {@code TenantService} (legacy signup); that class is untouched.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClinicRegistrationService {

    private final TenantRepository             tenantRepository;
    private final TenantUserRepository         tenantUserRepository;
    private final ClinicRepository             clinicRepository;
    private final PlanRepository               planRepository;
    private final TenantSubscriptionRepository subscriptionRepository;
    private final PlatformEventPublisher       eventPublisher;
    private final KeycloakUserService          keycloak;
    private final ApplicationEventPublisher    applicationEvents;

    private static final String DEFAULT_COUNTRY  = "IN";
    private static final String DEFAULT_LOCALE    = "en-IN";
    private static final String DEFAULT_CURRENCY  = "INR";

    /** Bounds the slug-retry loop below — a handful of collisions is realistic, an unbounded loop isn't. */
    private static final int MAX_SLUG_ATTEMPTS = 5;

    @Transactional
    public ClinicRegistrationResponse register(ClinicRegistrationRequest request) {
        // @Transactional here too: the call below is a self-invocation and bypasses the proxy.
        return register(request, true);
    }

    /**
     * @param sendEmailVerification whether to email a verification code once the signup
     *                              commits. Bulk imports pass {@code false} so we don't mail
     *                              hundreds of clinics at once; their admins verify on demand.
     */
    @Transactional
    public ClinicRegistrationResponse register(ClinicRegistrationRequest request, boolean sendEmailVerification) {
        String baseSlug = generateSlug(request.getClinicName());
        String slug = uniqueSlug(baseSlug);
        if (tenantRepository.existsByEmail(request.getAdminEmail())) {
            throw new IllegalArgumentException("Email already registered: " + request.getAdminEmail());
        }

        TenantType type = request.isPrivatePractice() ? TenantType.PRIVATE_CLINIC : TenantType.CLINIC;
        String displayName = StringUtils.hasText(request.getDisplayName())
                ? request.getDisplayName() : request.getClinicName();

        String keycloakOrgId  = null;
        String keycloakUserId = null;
        try {
            OrganisationProvision provision = provisionOrganisation(baseSlug, slug);
            slug = provision.slug();
            keycloakOrgId = provision.keycloakOrgId();

            String adminUsername = slug + ".admin";
            keycloakUserId = keycloak.createUser(adminUsername, request.getAdminEmail(),
                    request.getAdminFirstName(), request.getAdminLastName(),
                    request.getAdminPassword(), false);

            keycloak.addOrganisationMember(keycloakOrgId, keycloakUserId);
            keycloak.assignRealmRole(keycloakUserId, PlatformRole.TENANTADMIN);

            Tenant tenant = Tenant.builder()
                    .tenantId(keycloakOrgId)
                    .name(request.getClinicName())
                    .displayName(displayName)
                    .email(request.getAdminEmail())
                    .slug(slug)
                    .type(type)
                    .plan(TenantPlan.free)
                    .status(TenantStatus.active)
                    .verificationStatus(VerificationStatus.PENDING)
                    .listed(false)
                    .mobile(request.getMobile())
                    .website(request.getWebsite())
                    .country(defaulted(request.getCountry(), DEFAULT_COUNTRY))
                    .locale(defaulted(request.getLocale(), DEFAULT_LOCALE))
                    .currency(defaulted(request.getCurrency(), DEFAULT_CURRENCY))
                    .effectiveStartDate(Instant.now())
                    .build();
            tenant = tenantRepository.save(tenant);

            TenantUser adminUser = TenantUser.builder()
                    .tenant(tenant)
                    .authUserId(UUID.fromString(keycloakUserId))
                    .username(adminUsername)
                    .email(request.getAdminEmail())
                    .firstName(request.getAdminFirstName())
                    .lastName(request.getAdminLastName())
                    .mobile(request.getMobile())
                    .role(PlatformRole.TENANTADMIN)
                    .status("ACTIVE")
                    .build();
            adminUser = tenantUserRepository.save(adminUser);

            Clinic clinic = new Clinic();
            clinic.setTenant(tenant);
            clinic.setName(request.getClinicName());
            clinic.setEmail(request.getAdminEmail());
            clinic.setPhone(request.getMobile());
            clinic.setWebsite(request.getWebsite());
            clinic.setCountry(tenant.getCountry());
            clinic.setCategory(parseCategory(request.getClinicCategory()));
            if (request.getSpecialties() != null) {
                clinic.setSpecialties(request.getSpecialties());
            }
            clinic = clinicRepository.save(clinic);

            assignFreePlan(tenant);

            finalizeKeycloakOrg(keycloakOrgId, slug);

            if (sendEmailVerification) {
                // Sent after commit, so a mail failure can't fail the signup.
                applicationEvents.publishEvent(new ClinicEmailVerificationService.Requested(tenant.getId()));
            }

            eventPublisher.publishClinicCreated(ClinicCreatedEvent.builder()
                    .tenantId(tenant.getId())
                    .clinicId(clinic.getId())
                    .clinicName(tenant.getName())
                    .displayName(tenant.getDisplayName())
                    .slug(tenant.getSlug())
                    .email(tenant.getEmail())
                    .plan(tenant.getPlan().name())
                    .type(type.name())
                    .verificationStatus(tenant.getVerificationStatus().name())
                    .build());

            log.info("Clinic registered: slug={} type={} keycloakOrgId={} admin={}",
                    slug, type, keycloakOrgId, adminUsername);

            return ClinicRegistrationResponse.builder()
                    .tenantId(tenant.getId())
                    .clinicId(clinic.getId())
                    .clinicName(tenant.getName())
                    .displayName(tenant.getDisplayName())
                    .slug(tenant.getSlug())
                    .type(type)
                    .verificationStatus(tenant.getVerificationStatus())
                    .listed(tenant.isListed())
                    .keycloakOrgId(keycloakOrgId)
                    .adminUserId(adminUser.getId())
                    .adminUsername(adminUsername)
                    .keycloakUserId(keycloakUserId)
                    .plan(tenant.getPlan().name())
                    .createdAt(tenant.getCreatedAt())
                    .emailVerified(false)
                    .nextSteps(List.of(
                            "Verify your email address — enter the 6-digit code we sent to the admin email",
                            "Complete your clinic's legal profile (registered name, address, tax id)",
                            "Upload your clinical establishment registration and biomedical waste authorisation",
                            "Submit for review — your clinic is listed in search once verified"))
                    .build();

        } catch (Exception ex) {
            log.error("Clinic registration failed for slug='{}': {}", slug, ex.getMessage(), ex);
            keycloak.deleteUserQuietly(keycloakUserId);
            keycloak.deleteOrganisationQuietly(keycloakOrgId);
            throw ex;
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────

    /**
     * Clears the pending tag Keycloak set on org creation, now that this registration
     * has committed. Failure here must NOT fail the registration or trigger the
     * Keycloak cleanup in the outer catch — the tenant is fully created either way;
     * a stuck tag just gets cleared later by {@code KeycloakOrganisationReconciliationJob}.
     */
    private void finalizeKeycloakOrg(String keycloakOrgId, String slug) {
        try {
            keycloak.finalizeOrganisation(keycloakOrgId);
        } catch (Exception ex) {
            log.warn("Tenant '{}' registered but failed to clear Keycloak provisioning tag for org {}: {} " +
                    "— reconciliation job will retry.", slug, keycloakOrgId, ex.getMessage());
        }
    }

    private void assignFreePlan(Tenant tenant) {
        planRepository.findByCode(TenantPlan.free).ifPresentOrElse(
                freePlan -> subscriptionRepository.save(TenantSubscription.builder()
                        .tenant(tenant)
                        .plan(freePlan)
                        .status(SubscriptionStatus.ACTIVE)
                        .currentPeriodStart(Instant.now())
                        .build()),
                () -> log.warn("Free plan not found in DB — skipping subscription for tenant '{}'.", tenant.getSlug())
        );
    }

    private static ClinicCategory parseCategory(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        try {
            return ClinicCategory.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown clinicCategory: " + raw);
        }
    }

    static String generateSlug(String name) {
        return name.toLowerCase()
                   .replaceAll("[^a-z0-9]+", "-")
                   .replaceAll("(^-+)|(-+$)", "");
    }

    private String uniqueSlug(String base) {
        if (!tenantRepository.existsBySlug(base)) {
            return base;
        }
        int suffix = 2;
        String candidate;
        do {
            candidate = base + "-" + suffix++;
        } while (tenantRepository.existsBySlug(candidate));
        return candidate;
    }

    private record OrganisationProvision(String slug, String keycloakOrgId) {}

    /**
     * Creates the Keycloak org for {@code slug}, retrying with the next available
     * slug if Keycloak reports the alias as genuinely taken (not an in-flight or
     * stale-and-reapable attempt) — e.g. a leftover org from test data that was
     * cleared out of Postgres without going through Keycloak. {@code uniqueSlug()}
     * alone can't catch this since it only checks the local Tenant table.
     */
    private OrganisationProvision provisionOrganisation(String baseSlug, String slug) {
        for (int attempt = 1; ; attempt++) {
            try {
                return new OrganisationProvision(slug, keycloak.createOrganisation(slug));
            } catch (OrganisationAliasTakenException ex) {
                if (attempt >= MAX_SLUG_ATTEMPTS) {
                    throw ex;
                }
                String next = nextAvailableSlug(baseSlug, slug);
                log.warn("Slug '{}' is already registered in Keycloak with no matching tenant " +
                        "(stale data?) — retrying registration with '{}'.", slug, next);
                slug = next;
            }
        }
    }

    /** Next slug to try after {@code taken}, skipping any suffix the local Tenant table already holds. */
    private String nextAvailableSlug(String base, String taken) {
        int suffix = taken.equals(base) ? 1 : parseSuffix(base, taken);
        String candidate;
        do {
            candidate = base + "-" + (++suffix);
        } while (tenantRepository.existsBySlug(candidate));
        return candidate;
    }

    private static int parseSuffix(String base, String slug) {
        try {
            return Integer.parseInt(slug.substring(base.length() + 1));
        } catch (RuntimeException ex) {
            return 1;
        }
    }

    private static String defaulted(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
