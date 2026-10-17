package com.flowbinary.platformservice.service.dataimport;

import com.flowbinary.platformservice.domain.AffiliationType;
import com.flowbinary.platformservice.domain.Clinic;
import com.flowbinary.platformservice.domain.Doctor;
import com.flowbinary.platformservice.domain.Gender;
import com.flowbinary.platformservice.dto.dataimport.ImportOutcome;
import com.flowbinary.platformservice.dto.dataimport.ImportResult;
import com.flowbinary.platformservice.dto.dataimport.ImportRowResult;
import com.flowbinary.platformservice.dto.registration.DoctorRegistrationRequest;
import com.flowbinary.platformservice.repository.DoctorRepository;
import com.flowbinary.platformservice.service.DoctorClinicMappingService;
import com.flowbinary.platformservice.service.DoctorClinicMappingService.MappingSpec;
import com.flowbinary.platformservice.service.DoctorRegistrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

/**
 * Bulk-imports doctors from a spreadsheet, optionally attaching each to a clinic
 * in the same row.
 *
 * <p>Columns (header names, case / spacing insensitive):
 * <ul>
 *   <li><b>firstName</b>, <b>lastName</b>, <b>email</b>, <b>phone</b> (required)</li>
 *   <li>password — generated &amp; returned when blank (new doctors only)</li>
 *   <li>gender, primarySpecialty, registrationNumber, registrationCouncil, city, country</li>
 *   <li>clinicSlug (or clinicName), affiliationType, designation, department,
 *       consultationFee, currency, primaryClinic (true/false) — optional mapping</li>
 * </ul>
 * A repeated email reuses the existing doctor and just adds the mapping.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DoctorImportService {

    private static final List<String> REQUIRED = List.of("firstName", "lastName", "email", "phone");

    private final DoctorRepository            doctorRepository;
    private final DoctorRegistrationService   doctorRegistrationService;
    private final DoctorClinicMappingService  mappingService;

    public ImportResult importDoctors(InputStream spreadsheet) throws IOException {
        List<SpreadsheetReader.Row> rows = SpreadsheetReader.read(spreadsheet, REQUIRED);
        ImportResult result = new ImportResult();

        for (SpreadsheetReader.Row row : rows) {
            try {
                result.add(importRow(row));
            } catch (Exception ex) {
                log.warn("Doctor import row {} failed: {}", row.number(), ex.getMessage());
                result.add(ImportRowResult.builder()
                        .row(row.number())
                        .outcome(ImportOutcome.FAILED)
                        .reference(row.get("email"))
                        .message(ex.getMessage())
                        .build());
            }
        }
        return result;
    }

    private ImportRowResult importRow(SpreadsheetReader.Row row) {
        String email = row.require("email").toLowerCase();
        String clinicRef = firstNonBlank(row.get("clinicSlug"), row.get("clinicName"), row.get("clinic"));

        Doctor existing = doctorRepository.findByEmailIgnoreCase(email).orElse(null);

        UUID doctorId;
        boolean createdDoctor = false;
        String generatedPassword = null;

        if (existing != null) {
            doctorId = existing.getId();
        } else {
            String password = GeneratedPasswords.generate();
            generatedPassword = password;
            var req = buildRequest(row, email, password);
            // No codes on bulk import — doctors request them from the verification page at first sign-in.
            doctorId = doctorRegistrationService.register(req, false).getDoctorId();
            createdDoctor = true;
        }

        String mappingNote = "";
        boolean mappingChanged = false;
        boolean mappingFailed = false;
        if (clinicRef != null) {
            try {
                Clinic clinic = mappingService.resolveClinic(clinicRef);
                var mapOutcome = mappingService.upsert(doctorId, clinic.getId(), mappingSpec(row));
                mappingChanged = true;
                mappingNote = " — " + (mapOutcome.created() ? "linked to" : "mapping updated for")
                        + " clinic '" + clinic.getName() + "'";
            } catch (Exception ex) {
                mappingFailed = true;
                mappingNote = " — clinic mapping FAILED: " + ex.getMessage();
            }
        }

        ImportOutcome outcome;
        if (mappingFailed && !createdDoctor) {
            outcome = ImportOutcome.FAILED;
        } else if (createdDoctor || mappingChanged) {
            outcome = createdDoctor ? ImportOutcome.CREATED : ImportOutcome.UPDATED;
        } else {
            outcome = ImportOutcome.SKIPPED;
        }
        String message = (createdDoctor ? "Doctor created" : "Doctor already registered") + mappingNote;

        return ImportRowResult.builder()
                .row(row.number())
                .outcome(outcome)
                .reference(email)
                .message(message)
                .generatedPassword(generatedPassword)
                .build();
    }

    private DoctorRegistrationRequest buildRequest(SpreadsheetReader.Row row, String email, String password) {
        var req = new DoctorRegistrationRequest();
        req.setFirstName(row.require("firstName"));
        req.setLastName(row.require("lastName"));
        req.setEmail(email);
        req.setPassword(password);
        req.setPhone(row.require("phone"));
        req.setGender(parseGender(row.get("gender")));
        req.setPrimarySpecialty(row.get("primarySpecialty"));
        req.setRegistrationNumber(row.get("registrationNumber"));
        req.setRegistrationCouncil(row.get("registrationCouncil"));
        req.setCity(row.get("city"));
        req.setCountry(row.get("country"));
        return req;
    }

    private MappingSpec mappingSpec(SpreadsheetReader.Row row) {
        return MappingSpec.builder()
                .affiliationType(parseAffiliationType(row.get("affiliationType")))
                .designation(row.get("designation"))
                .department(row.get("department"))
                .consultationFee(row.getDecimal("consultationFee"))
                .currency(row.get("currency"))
                .primaryClinic(row.get("primaryClinic") == null ? null : row.getBoolean("primaryClinic"))
                .status(row.get("status"))
                .build();
    }

    private static Gender parseGender(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        try {
            return Gender.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown gender: " + raw);
        }
    }

    private static AffiliationType parseAffiliationType(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        try {
            return AffiliationType.valueOf(raw.trim().toUpperCase().replace('-', '_').replace(' ', '_'));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown affiliationType: " + raw);
        }
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (StringUtils.hasText(v)) return v.trim();
        }
        return null;
    }
}
