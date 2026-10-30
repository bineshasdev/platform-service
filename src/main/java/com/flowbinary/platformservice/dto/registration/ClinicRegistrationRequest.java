package com.flowbinary.platformservice.dto.registration;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Minimal payload for POST /api/v1/registration/clinics.
 *
 * <p>Only the clinic name and one admin user are required — legal identity and
 * licensing credentials are collected afterwards and do not block signup.
 */
@Getter
@Setter
public class ClinicRegistrationRequest {

    @NotBlank(message = "Clinic name is required")
    @Size(max = 100)
    private String clinicName;

    @Size(max = 100)
    private String displayName;

    // ── First admin user ──────────────────────────────────────────────

    @NotBlank(message = "Admin first name is required")
    @Size(max = 50)
    private String adminFirstName;

    @NotBlank(message = "Admin last name is required")
    @Size(max = 50)
    private String adminLastName;

    @NotBlank(message = "Admin email is required")
    @Email(message = "Admin email must be a valid address")
    private String adminEmail;

    @NotBlank(message = "Admin password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String adminPassword;

    // ── Optional contact / locale ─────────────────────────────────────

    @Size(max = 20)
    private String mobile;

    @Size(max = 255)
    private String website;

    /** ISO 3166-1 alpha-2 country code. Defaults to "IN". */
    @Size(min = 2, max = 2)
    private String country;

    /** BCP-47 locale tag. Defaults to "en-IN". */
    @Size(max = 10)
    private String locale;

    /** ISO 4217 currency code. Defaults to "INR". */
    @Size(min = 3, max = 3)
    private String currency;

    // ── Optional clinic profile ──────────────────────────────────────

    /** GENERAL / SINGLE_SPECIALTY / MULTI_SPECIALTY / DENTAL / EYE_CARE / AYUSH. */
    @Size(max = 30)
    private String clinicCategory;

    private List<String> specialties;
}
