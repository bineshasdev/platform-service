package com.flowbinary.platformservice.event;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/**
 * Emitted when a tenant's verification status changes — in particular when it
 * transitions to {@code VERIFIED} / {@code listed = true}, which is the signal
 * for the search / directory service to (de)index the tenant.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class TenantVerificationChangedEvent extends PlatformEvent {

    private UUID tenantId;
    private String tenantName;
    private String slug;
    private String type;
    private String previousStatus;
    private String newStatus;
    private boolean listed;
    private String changedBy;

    @Override
    public String getEventType() {
        return "TENANT_VERIFICATION_CHANGED";
    }
}
