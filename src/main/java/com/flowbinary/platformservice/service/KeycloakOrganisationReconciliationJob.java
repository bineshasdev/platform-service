package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.representations.idm.OrganizationRepresentation;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Sweeps Keycloak organisations still tagged {@code provisioningStatus=PENDING} —
 * left behind when a signup (e.g. {@link ClinicRegistrationService#register}) is
 * killed after creating the org in Keycloak but before its local Tenant row commits,
 * or before the finalize call clears the tag.
 *
 * <p>Self-heals rather than deletes whenever a matching Tenant already exists —
 * that means registration itself succeeded and only the finalize step failed.
 * Only orgs with no matching Tenant, past the staleness window, are torn down.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KeycloakOrganisationReconciliationJob {

    private static final Duration STALE_AFTER = Duration.ofMinutes(30);

    private final KeycloakUserService keycloak;
    private final TenantRepository    tenantRepository;

    @Scheduled(cron = "0 */10 * * * *")
    public void sweepPendingOrganisations() {
        List<OrganizationRepresentation> pending = keycloak.findPendingOrganisations();
        for (OrganizationRepresentation org : pending) {
            try {
                reconcile(org);
            } catch (Exception ex) {
                log.error("Reconciliation failed for Keycloak org {}: {}", org.getId(), ex.getMessage(), ex);
            }
        }
    }

    private void reconcile(OrganizationRepresentation org) {
        Instant startedAt = KeycloakUserService.provisioningStartedAt(org);
        if (startedAt == null || Instant.now().isBefore(startedAt.plus(STALE_AFTER))) {
            return;
        }

        if (tenantRepository.findByTenantId(org.getId()).isPresent()) {
            log.warn("Org {} ('{}') has a committed tenant but is still tagged pending — finalizing.",
                    org.getId(), org.getName());
            keycloak.finalizeOrganisation(org.getId());
            return;
        }

        log.warn("Org {} ('{}') has been pending for over {} with no matching tenant — reaping as orphaned.",
                org.getId(), org.getName(), STALE_AFTER);
        keycloak.listMemberIds(org.getId()).forEach(keycloak::deleteUserQuietly);
        keycloak.deleteOrganisationQuietly(org.getId());
    }
}
