package com.flowbinary.platformservice.dto.registration;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;
import java.util.UUID;

/**
 * View of a tenant's legal profile.
 */
@Getter
@Builder
public class LegalProfileResponse {

    private UUID tenantId;
    private String legalName;
    private String entityType;
    private String registrationNumber;
    private String taxId;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String zip;
    private String country;
    private String contactEmail;
    private String contactPhone;
    private Map<String, Object> dataProtection;
    private Map<String, Object> attributes;
}
