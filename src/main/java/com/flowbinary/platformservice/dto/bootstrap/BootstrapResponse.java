package com.flowbinary.platformservice.dto.bootstrap;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BootstrapResponse {
    private PlatformInfo         platform;
    private UserInfo             user;
    private TenantInfo           tenant;
    private BrandingInfo         branding;
    private ThemeInfo            theme;
    private Map<String, Boolean> features;
    private SubscriptionInfo     subscription;
    private List<MenuEntry>      menus;
    private List<MenuEntry>      settingsMenu;
    private NotificationsInfo    notifications;
    private IntegrationsInfo     integrations;
    private EnvironmentInfo      environment;
}
