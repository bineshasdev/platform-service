package com.flowbinary.platformservice.event;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.UUID;

/**
 * Published after a TenantUser profile, role, or status is updated.
 * {@code changedFields} lists the field names that were actually modified so
 * consumers can decide whether to trigger a notification (e.g. only notify on
 * role or status changes, not on minor profile edits).
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class UserUpdatedEvent extends PlatformEvent {

    private UUID tenantId;
    private UUID userId;
    private UUID authUserId;

    private String email;
    private String firstName;
    private String lastName;
    /** Code of the member's primary role in the authorization model. */
    private String role;
    private String status;

    /** Names of fields that changed in this update (e.g. ["role", "status"]). */
    private List<String> changedFields;

    /** The actor (admin/superadmin) who triggered the update. */
    private UUID actorId;

    @Override
    public String getEventType() {
        return "USER_UPDATED";
    }
}
