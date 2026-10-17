package com.flowbinary.platformservice.dto.bootstrap;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NotificationsInfo {
    private int unreadCount;
}
