package com.flowbinary.platformservice.dto.test;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

public record NotificationTestRequest(

        /** Avro eventType — e.g. USER_CREATED, USER_UPDATED, USER_DELETED,
         *  TENANT_CREATED, TENANT_PLAN_CHANGED, FEATURE_TOGGLED, MENU_ITEM_UPDATED */
        @NotBlank String eventType,

        /** Target tenant UUID. Defaults to the authenticated caller's tenant when omitted. */
        String tenantId,

        /** Audience scope: USER | TENANT | SYSTEM. Defaults to TENANT when omitted. */
        String scope,

        /** User UUIDs to target (only relevant when scope = USER). */
        List<String> userIds,

        /** Freeform template data — key/value pairs passed to the notification template. */
        Map<String, String> data
) {}
