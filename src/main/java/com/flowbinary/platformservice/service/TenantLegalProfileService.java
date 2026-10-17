package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.dto.registration.LegalProfileRequest;
import com.flowbinary.platformservice.dto.registration.LegalProfileResponse;
import com.flowbinary.platformservice.model.Tenant;
import com.flowbinary.platformservice.model.TenantLegalProfile;
import com.flowbinary.platformservice.repository.TenantLegalProfileRepository;
import com.flowbinary.platformservice.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Manages a tenant's country-aware legal identity ({@link TenantLegalProfile}) —
 * the registered entity a licence is issued to. One row per tenant.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantLegalProfileService {

    private final TenantRepository             tenantRepository;
    private final TenantLegalProfileRepository legalProfileRepository;

    @Transactional
    public LegalProfileResponse upsert(UUID tenantId, LegalProfileRequest req) {
        Tenant tenant = getTenant(tenantId);

        TenantLegalProfile profile = legalProfileRepository.findByTenantId(tenantId)
                .orElseGet(() -> TenantLegalProfile.builder().tenant(tenant).build());

        profile.setLegalName(req.getLegalName());
        profile.setEntityType(req.getEntityType());
        profile.setRegistrationNumber(req.getRegistrationNumber());
        profile.setTaxId(req.getTaxId());
        profile.setAddressLine1(req.getAddressLine1());
        profile.setAddressLine2(req.getAddressLine2());
        profile.setCity(req.getCity());
        profile.setState(req.getState());
        profile.setZip(req.getZip());
        profile.setCountry(StringUtils.hasText(req.getCountry()) ? req.getCountry() : tenant.getCountry());
        profile.setContactEmail(req.getContactEmail());
        profile.setContactPhone(req.getContactPhone());
        profile.setDataProtection(req.getDataProtection());
        profile.setAttributes(req.getAttributes());

        profile = legalProfileRepository.save(profile);
        log.debug("Legal profile saved for tenant {}", tenant.getSlug());
        return toResponse(tenantId, profile);
    }

    @Transactional(readOnly = true)
    public LegalProfileResponse get(UUID tenantId) {
        getTenant(tenantId);
        return legalProfileRepository.findByTenantId(tenantId)
                .map(p -> toResponse(tenantId, p))
                .orElseThrow(() -> new NoSuchElementException("No legal profile for tenant " + tenantId));
    }

    // ── internal ──────────────────────────────────────────────────────────

    private Tenant getTenant(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new NoSuchElementException("Tenant not found: " + tenantId));
    }

    private LegalProfileResponse toResponse(UUID tenantId, TenantLegalProfile p) {
        return LegalProfileResponse.builder()
                .tenantId(tenantId)
                .legalName(p.getLegalName())
                .entityType(p.getEntityType())
                .registrationNumber(p.getRegistrationNumber())
                .taxId(p.getTaxId())
                .addressLine1(p.getAddressLine1())
                .addressLine2(p.getAddressLine2())
                .city(p.getCity())
                .state(p.getState())
                .zip(p.getZip())
                .country(p.getCountry())
                .contactEmail(p.getContactEmail())
                .contactPhone(p.getContactPhone())
                .dataProtection(p.getDataProtection())
                .attributes(p.getAttributes())
                .build();
    }
}
