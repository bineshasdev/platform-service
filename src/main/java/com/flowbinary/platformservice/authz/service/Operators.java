package com.flowbinary.platformservice.authz.service;

import com.flowbinary.platformservice.authz.sdk.PermissionCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Whether a user is a platform operator: an active platform membership that can manage platform templates. */
@Component
@RequiredArgsConstructor
public class Operators {

    /** The privilege that makes someone an operator for the admin screens that act across tenants. */
    public static final String OPERATOR_PRIVILEGE = "platform.template.manage";

    private final EffectivePermissionService permissions;
    private final PermissionCache cache;

    public boolean is(UUID userSub) {
        return cache.get(null, userSub, () -> permissions.forPlatform(userSub)).has(OPERATOR_PRIVILEGE);
    }
}
