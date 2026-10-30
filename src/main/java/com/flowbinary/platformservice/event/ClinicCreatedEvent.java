package com.flowbinary.platformservice.event;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/**
 * Emitted by the clinic self-signup flow — distinct from the legacy
 * {@link TenantCreatedEvent} so clinic-specific consumers (e.g. clinic directory
 * indexing, onboarding emails) don't have to filter a generic tenant event by type.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class ClinicCreatedEvent extends PlatformEvent {

    private UUID tenantId;
    private UUID clinicId;
    private String clinicName;
    private String displayName;
    private String slug;
    private String email;
    private String plan;

    /** Tenant business type (always CLINIC). */
    private String type;

    /** Verification status at creation — always PENDING for this flow. */
    private String verificationStatus;

    @Override
    public String getEventType() {
        return "CLINIC_CREATED";
    }
}
