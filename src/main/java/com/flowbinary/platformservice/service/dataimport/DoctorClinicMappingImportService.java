package com.flowbinary.platformservice.service.dataimport;

import com.flowbinary.platformservice.domain.AffiliationType;
import com.flowbinary.platformservice.domain.Clinic;
import com.flowbinary.platformservice.domain.Doctor;
import com.flowbinary.platformservice.dto.dataimport.ImportOutcome;
import com.flowbinary.platformservice.dto.dataimport.ImportResult;
import com.flowbinary.platformservice.dto.dataimport.ImportRowResult;
import com.flowbinary.platformservice.service.DoctorClinicMappingService;
import com.flowbinary.platformservice.service.DoctorClinicMappingService.MappingSpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Bulk-imports doctor ⇄ clinic affiliations. Both the doctor and the clinic must
 * already exist.
 *
 * <p>Columns (header names, case / spacing insensitive):
 * <ul>
 *   <li><b>doctorEmail</b> (required)</li>
 *   <li><b>clinicSlug</b> — or <b>clinicName</b> / <b>clinic</b> (required)</li>
 *   <li>affiliationType (FULL_TIME / VISITING / CONSULTANT / LOCUM)</li>
 *   <li>designation, department, consultationFee, currency</li>
 *   <li>primaryClinic (true/false), status</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DoctorClinicMappingImportService {

    private static final List<String> REQUIRED = List.of("doctorEmail");

    private final DoctorClinicMappingService mappingService;

    public ImportResult importMappings(InputStream spreadsheet) throws IOException {
        List<SpreadsheetReader.Row> rows = SpreadsheetReader.read(spreadsheet, REQUIRED);
        ImportResult result = new ImportResult();

        for (SpreadsheetReader.Row row : rows) {
            String email = row.get("doctorEmail");
            String clinicRef = firstNonBlank(row.get("clinicSlug"), row.get("clinicName"), row.get("clinic"));
            try {
                Doctor doctor = mappingService.resolveDoctorByEmail(row.require("doctorEmail"));
                if (clinicRef == null) {
                    throw new IllegalArgumentException("Missing clinicSlug / clinicName");
                }
                Clinic clinic = mappingService.resolveClinic(clinicRef);

                var outcome = mappingService.upsert(doctor.getId(), clinic.getId(), spec(row));

                result.add(ImportRowResult.builder()
                        .row(row.number())
                        .outcome(outcome.created() ? ImportOutcome.CREATED : ImportOutcome.UPDATED)
                        .reference(email + " @ " + clinicRef)
                        .message((outcome.created() ? "Linked" : "Updated") + " " + doctor.getFirstName()
                                + " " + doctor.getLastName() + " ⇄ " + clinic.getName())
                        .build());
            } catch (Exception ex) {
                log.warn("Mapping import row {} failed: {}", row.number(), ex.getMessage());
                result.add(ImportRowResult.builder()
                        .row(row.number())
                        .outcome(ImportOutcome.FAILED)
                        .reference(email + (clinicRef == null ? "" : " @ " + clinicRef))
                        .message(ex.getMessage())
                        .build());
            }
        }
        return result;
    }

    private MappingSpec spec(SpreadsheetReader.Row row) {
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
