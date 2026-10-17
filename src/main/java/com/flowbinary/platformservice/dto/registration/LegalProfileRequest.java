package com.flowbinary.platformservice.dto.registration;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * Payload for PUT /api/v1/registration/{tenantId}/legal-profile.
 * All fields optional — the tenant can fill this in incrementally.
 */
@Getter
@Setter
public class LegalProfileRequest {

    @Size(max = 255)
    private String legalName;

    @Size(max = 50)
    private String entityType;

    @Size(max = 120)
    private String registrationNumber;

    @Size(max = 120)
    private String taxId;

    @Size(max = 255)
    private String addressLine1;

    @Size(max = 255)
    private String addressLine2;

    @Size(max = 120)
    private String city;

    @Size(max = 120)
    private String state;

    @Size(max = 20)
    private String zip;

    @Size(min = 2, max = 2)
    private String country;

    @Email
    @Size(max = 255)
    private String contactEmail;

    @Size(max = 30)
    private String contactPhone;

    private Map<String, Object> dataProtection;

    private Map<String, Object> attributes;
}
