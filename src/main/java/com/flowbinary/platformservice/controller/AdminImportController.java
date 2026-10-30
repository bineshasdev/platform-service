package com.flowbinary.platformservice.controller;

import com.flowbinary.platformservice.dto.dataimport.ImportResult;
import com.flowbinary.platformservice.service.dataimport.ClinicImportService;
import com.flowbinary.platformservice.service.dataimport.DoctorClinicMappingImportService;
import com.flowbinary.platformservice.service.dataimport.DoctorImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * SUPERADMIN-only bulk import of clinics, doctors and their mappings from Excel
 * ({@code .xlsx} / {@code .xls}) spreadsheets.
 *
 * <p>Each endpoint takes a single multipart {@code file}. Rows are processed
 * independently; the response reports per-row outcomes.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/imports")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_SUPERADMIN')")
@Tag(name = "Admin — Imports", description = "Bulk import (SUPERADMIN)")
public class AdminImportController {

    private final ClinicImportService              clinicImportService;
    private final DoctorImportService              doctorImportService;
    private final DoctorClinicMappingImportService mappingImportService;

    @PostMapping(value = "/clinics", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import clinics from a spreadsheet",
            description = "Columns: clinicName*, displayName, adminFirstName*, adminLastName*, "
                    + "adminEmail*, adminPassword, mobile, website, country, locale, currency, clinicCategory, specialties.")
    @ApiResponse(responseCode = "200", description = "Import processed — see per-row outcomes")
    ImportResult importClinics(@RequestParam("file") MultipartFile file) throws IOException {
        return clinicImportService.importClinics(openSpreadsheet(file));
    }

    @PostMapping(value = "/doctors", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import doctors (with optional inline clinic mapping)",
            description = "Columns: firstName*, lastName*, email*, phone*, password, gender, primarySpecialty, "
                    + "registrationNumber, registrationCouncil, city, country, clinicSlug|clinicName, affiliationType, "
                    + "designation, department, consultationFee, currency, primaryClinic.")
    @ApiResponse(responseCode = "200", description = "Import processed — see per-row outcomes")
    ImportResult importDoctors(@RequestParam("file") MultipartFile file) throws IOException {
        return doctorImportService.importDoctors(openSpreadsheet(file));
    }

    @PostMapping(value = "/doctor-clinic-mappings", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import doctor ⇄ clinic affiliations",
            description = "Columns: doctorEmail*, clinicSlug|clinicName*, affiliationType, designation, department, "
                    + "consultationFee, currency, primaryClinic, status. Doctor and clinic must already exist.")
    @ApiResponse(responseCode = "200", description = "Import processed — see per-row outcomes")
    ImportResult importMappings(@RequestParam("file") MultipartFile file) throws IOException {
        return mappingImportService.importMappings(openSpreadsheet(file));
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private static java.io.InputStream openSpreadsheet(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file uploaded");
        }
        String name = file.getOriginalFilename();
        if (name != null && !(name.toLowerCase().endsWith(".xlsx") || name.toLowerCase().endsWith(".xls"))) {
            throw new IllegalArgumentException("Expected an Excel file (.xlsx or .xls), got: " + name);
        }
        return file.getInputStream();
    }
}
