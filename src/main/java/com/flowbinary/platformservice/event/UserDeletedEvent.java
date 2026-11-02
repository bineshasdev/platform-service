package com.flowbinary.platformservice.event;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/**
 * Published after a TenantUser has been removed from both Keycloak and the DB.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class UserDeletedEvent extends PlatformEvent {

    private UUID tenantId;
    private UUID userId;
    private UUID authUserId;

    private String email;
    private String firstName;
    private String lastName;
    /** Code of the member's primary role in the authorization model. */
    private String role;

    /** The actor who triggered the deletion. */
    private UUID actorId;

    @Override
    public String getEventType() {
        return "USER_DELETED";
    }
}
