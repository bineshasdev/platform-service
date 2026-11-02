package com.flowbinary.platformservice.event;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/**
 * Published after a new TenantUser is successfully persisted and created in Keycloak.
 * Consumed by the notification-service to send welcome / creation alerts.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class UserCreatedEvent extends PlatformEvent {

    /** Tenant the user belongs to. */
    private UUID tenantId;

    /** Platform (DB) user ID. */
    private UUID userId;

    /** Keycloak user UUID (maps to JWT sub). */
    private UUID authUserId;

    private String email;
    private String firstName;
    private String lastName;
    /** Code of the member's primary role in the authorization model. */
    private String role;

    /** The actor (admin/superadmin) who triggered the creation. */
    private UUID actorId;

    @Override
    public String getEventType() {
        return "USER_CREATED";
    }
}
