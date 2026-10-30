package com.flowbinary.platformservice.service.dataimport;

import com.flowbinary.platformservice.dto.dataimport.ImportOutcome;
import com.flowbinary.platformservice.dto.dataimport.ImportResult;
import com.flowbinary.platformservice.dto.dataimport.ImportRowResult;
import com.flowbinary.platformservice.dto.registration.ClinicRegistrationRequest;
import com.flowbinary.platformservice.dto.registration.ClinicRegistrationResponse;
import com.flowbinary.platformservice.service.ClinicRegistrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Bulk-imports clinics from an {@code .xlsx}/{@code .xls} sheet. Each row creates
 * a tenant + admin login + clinic profile via {@link ClinicRegistrationService},
 * in its own transaction, so one bad row does not abort the batch.
 *
 * <p>Columns (header names, case / spacing insensitive):
 * <ul>
 *   <li><b>clinicName</b> (required)</li>
 *   <li>displayName</li>
 *   <li><b>adminFirstName</b>, <b>adminLastName</b>, <b>adminEmail</b> (required)</li>
 *   <li>adminPassword — generated &amp; returned when blank</li>
 *   <li>mobile, website, country, locale, currency</li>
 *   <li>clinicCategory, specialties (comma / semicolon separated)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClinicImportService {

    private static final List<String> REQUIRED =
            List.of("clinicName", "adminFirstName", "adminLastName", "adminEmail");

    private final ClinicRegistrationService clinicRegistrationService;

    public ImportResult importClinics(InputStream spreadsheet) throws IOException {
        List<SpreadsheetReader.Row> rows = SpreadsheetReader.read(spreadsheet, REQUIRED);
        ImportResult result = new ImportResult();

        for (SpreadsheetReader.Row row : rows) {
            try {
                result.add(importRow(row));
            } catch (Exception ex) {
                log.warn("Clinic import row {} failed: {}", row.number(), ex.getMessage());
                result.add(ImportRowResult.builder()
                        .row(row.number())
                        .outcome(ImportOutcome.FAILED)
                        .reference(row.get("clinicName"))
                        .message(ex.getMessage())
                        .build());
            }
        }
        return result;
    }

    private ImportRowResult importRow(SpreadsheetReader.Row row) {
        String suppliedPassword = row.get("adminPassword");
        String password = StringUtils.hasText(suppliedPassword) ? suppliedPassword : GeneratedPasswords.generate();

        ClinicRegistrationRequest req = new ClinicRegistrationRequest();
        req.setClinicName(row.require("clinicName"));
        req.setDisplayName(row.get("displayName"));
        req.setAdminFirstName(row.require("adminFirstName"));
        req.setAdminLastName(row.require("adminLastName"));
        req.setAdminEmail(row.require("adminEmail"));
        req.setAdminPassword(password);
        req.setMobile(row.get("mobile"));
        req.setWebsite(row.get("website"));
        req.setCountry(row.get("country"));
        req.setLocale(row.get("locale"));
        req.setCurrency(row.get("currency"));
        req.setClinicCategory(row.get("clinicCategory"));
        req.setSpecialties(row.getList("specialties"));

        ClinicRegistrationResponse created = clinicRegistrationService.register(req, false);

        return ImportRowResult.builder()
                .row(row.number())
                .outcome(ImportOutcome.CREATED)
                .reference(created.getSlug())
                .message("Clinic '" + created.getClinicName() + "' created (tenant " + created.getTenantId() + ")")
                .generatedPassword(StringUtils.hasText(suppliedPassword) ? null : password)
                .build();
    }
}
