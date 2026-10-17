package com.flowbinary.platformservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Request body for POST /api/v1/tenants/signup.
 */
@Getter
@Setter
public class TenantSignupRequest {

    // ── Tenant info ────────────────────────────────────────────────

    /** Human-readable organisation name, e.g. "Acme Corp". */
    @NotBlank(message = "Tenant name is required")
    @Size(max = 100, message = "Tenant name must be 100 characters or fewer")
    private String tenantName;

    /**
     * Display name shown in the UI. Defaults to {@code tenantName} when blank.
     */
    @Size(max = 100)
    private String displayName;

    // ── Admin user ─────────────────────────────────────────────────

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

    // ── Optional locale / contact ──────────────────────────────────

    @Size(max = 100)
    private String website;

    /** ISO 3166-1 alpha-3 country code. Defaults to "IN". */
    @Size(min = 2, max = 3)
    private String country;

    /** BCP-47 locale tag. Defaults to "en-GB". */
    @Size(max = 10)
    private String locale;

    /** ISO 4217 currency code. Defaults to "INR". */
    @Size(min = 3, max = 3)
    private String currency;
}
