package com.flowbinary.platformservice.event;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/**
 * Who-may-do-what changed. Every service holding cached permission sets drops
 * the affected entries: one user in one tenant, every user of a tenant
 * ({@code userSub} null), or everything ({@code tenantId} and {@code userSub} null,
 * e.g. a template changed).
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class AuthzChangedEvent extends PlatformEvent {

    /** Internal tenant id; null with {@code platform} true for operator memberships. */
    private UUID tenantId;
    private UUID userSub;
    /** True when the change concerns platform (operator) memberships. */
    private boolean platform;
    /** What changed, for logs: ROLE_CHANGED, MEMBERSHIP_CHANGED, TENANT_PROVISIONED… */
    private String reason;

    @Override
    public String getEventType() {
        return "AUTHZ_CHANGED";
    }
}
