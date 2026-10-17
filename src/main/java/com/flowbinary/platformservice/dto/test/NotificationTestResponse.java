package com.flowbinary.platformservice.dto.test;

import java.util.List;
import java.util.Map;

public record NotificationTestResponse(
        String              eventId,
        String              eventType,
        String              topic,
        String              tenantId,
        String              scope,
        List<String>        userIds,
        Map<String, String> data,
        String              status
) {}
