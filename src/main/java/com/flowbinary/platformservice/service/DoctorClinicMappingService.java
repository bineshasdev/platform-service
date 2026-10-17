package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.domain.AffiliationType;
import com.flowbinary.platformservice.domain.Clinic;
import com.flowbinary.platformservice.domain.Doctor;
import com.flowbinary.platformservice.domain.DoctorClinicAffiliation;
import com.flowbinary.platformservice.repository.ClinicRepository;
import com.flowbinary.platformservice.repository.DoctorClinicAffiliationRepository;
import com.flowbinary.platformservice.repository.DoctorRepository;
import com.flowbinary.platformservice.repository.TenantRepository;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Owns the {@link DoctorClinicAffiliation} many-to-many link. Shared by the bulk
 * importers and available for a future single-mapping admin endpoint.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DoctorClinicMappingService {

    private final DoctorRepository                    doctorRepository;
    private final ClinicRepository                    clinicRepository;
    private final TenantRepository                    tenantRepository;
    private final DoctorClinicAffiliationRepository   affiliationRepository;

    /** Mutable attributes of an affiliation; null fields are left unchanged on update. */
    @Builder
    public record MappingSpec(
            AffiliationType affiliationType,
            String designation,
            String department,
            BigDecimal consultationFee,
            String currency,
            Boolean primaryClinic,
            String status) {
    }

    public record UpsertOutcome(DoctorClinicAffiliation affiliation, boolean created) {}

    /**
     * Resolves a clinic from a spreadsheet reference: first by tenant slug, then
     * by exact (case-insensitive) clinic name when unambiguous.
     */
    @Transactional(readOnly = true)
    public Clinic resolveClinic(String reference) {
        if (!StringUtils.hasText(reference)) {
            throw new IllegalArgumentException("Clinic reference is blank");
        }
        String ref = reference.trim();

        var bySlug = tenantRepository.findBySlug(ref.toLowerCase())
                .flatMap(t -> clinicRepository.findByTenantId(t.getId()));
        if (bySlug.isPresent()) {
            return bySlug.get();
        }

        List<Clinic> byName = clinicRepository.findByNameIgnoreCase(ref);
        if (byName.size() == 1) {
            return byName.get(0);
        }
        if (byName.size() > 1) {
            throw new IllegalArgumentException("Clinic name '" + ref + "' is ambiguous — use the clinic slug");
        }
        throw new NoSuchElementException("No clinic found for '" + reference + "'");
    }

    @Transactional(readOnly = true)
    public Doctor resolveDoctorByEmail(String email) {
        return doctorRepository.findByEmailIgnoreCase(email.trim().toLowerCase())
                .orElseThrow(() -> new NoSuchElementException("No doctor registered with email '" + email + "'"));
    }

    /** Creates or updates the affiliation between a doctor and a clinic. */
    @Transactional
    public UpsertOutcome upsert(UUID doctorId, UUID clinicId, MappingSpec spec) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new NoSuchElementException("Doctor not found: " + doctorId));
        Clinic clinic = clinicRepository.findById(clinicId)
                .orElseThrow(() -> new NoSuchElementException("Clinic not found: " + clinicId));

        DoctorClinicAffiliation existing = affiliationRepository.findByDoctorIdAndClinicId(doctorId, clinicId).orElse(null);
        boolean created = existing == null;

        // Make room before promoting this one — the partial unique index allows one primary per doctor.
        if (Boolean.TRUE.equals(spec.primaryClinic())) {
            affiliationRepository.clearPrimaryForDoctor(doctorId);
        }

        DoctorClinicAffiliation aff = existing != null ? existing : newAffiliation(doctor, clinic);

        if (spec.affiliationType() != null)  aff.setAffiliationType(spec.affiliationType());
        if (spec.designation() != null)      aff.setDesignation(spec.designation());
        if (spec.department() != null)       aff.setDepartment(spec.department());
        if (spec.consultationFee() != null)  aff.setConsultationFee(spec.consultationFee());
        if (spec.currency() != null)         aff.setCurrency(spec.currency());
        if (spec.status() != null)           aff.setStatus(spec.status());
        if (spec.primaryClinic() != null)    aff.setPrimaryClinic(spec.primaryClinic());

        aff = affiliationRepository.save(aff);

        log.debug("Affiliation {} doctor={} clinic={} ({})",
                created ? "created" : "updated", doctorId, clinicId, aff.getId());
        return new UpsertOutcome(aff, created);
    }

    private static DoctorClinicAffiliation newAffiliation(Doctor doctor, Clinic clinic) {
        DoctorClinicAffiliation a = new DoctorClinicAffiliation();
        a.setDoctor(doctor);
        a.setClinic(clinic);
        return a;
    }
}
