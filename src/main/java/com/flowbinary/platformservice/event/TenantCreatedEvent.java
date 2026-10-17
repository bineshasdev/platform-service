package com.flowbinary.platformservice.event;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class TenantCreatedEvent extends PlatformEvent {

    private UUID tenantId;
    private String tenantName;
    private String slug;
    private String email;
    private String plan;

    /** Tenant business type (CLINIC, LAB, STORE, PRIVATE_CLINIC); null for legacy signups. */
    private String type;

    /** Verification status at creation — always PENDING for the registration flow. */
    private String verificationStatus;

    @Override
    public String getEventType() {
        return "TENANT_CREATED";
    }
}
