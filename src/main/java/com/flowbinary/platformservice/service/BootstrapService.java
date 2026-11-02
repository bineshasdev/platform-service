package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.authz.service.PrimaryRoles;
import com.flowbinary.platformservice.config.AppProperties;
import com.flowbinary.platformservice.dto.bootstrap.*;
import com.flowbinary.platformservice.model.*;
import com.flowbinary.platformservice.repository.*;
import com.flowbinary.platformservice.security.TenantContextResolver;
import com.flowbinary.platformservice.security.TenantNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BootstrapService {

    private final AppProperties                appProperties;
    private final PrimaryRoles                 primaryRoles;
    private final TenantContextResolver        tenantContextResolver;
    private final TenantUserRepository         tenantUserRepository;
    private final TenantSubscriptionRepository subscriptionRepository;
    private final PlanFeatureRepository        planFeatureRepository;
    private final MenuResolutionService        menuResolutionService;
    private final SettingsResolver             settingsResolver;

    // ── Feature codes that map to integrations ─────────────────────────────
    private static final Map<String, String> FEATURE_TO_INTEGRATION = Map.of(
        "erp_integration", "erp",
        "webhooks",        "webhooks",
        "api_access",      "api"
    );

    @Transactional(readOnly = true)
    public BootstrapResponse load(Authentication authentication, String applicationCode) {
        Jwt jwt = ((JwtAuthenticationToken) authentication).getToken();

        // ── Tenant ─────────────────────────────────────────────────────────
        Tenant tenant = tenantContextResolver.resolveRequired(authentication);

        // ── Current user ───────────────────────────────────────────────────
        UUID authUserId = UUID.fromString(jwt.getSubject());
        TenantUser user = tenantUserRepository
                .findByTenantIdAndAuthUserId(tenant.getId(), authUserId)
                .orElseThrow(() -> new TenantNotFoundException(
                        "User not found in tenant: " + authUserId));

        // ── Subscription + plan ────────────────────────────────────────────
        TenantSubscription subscription = subscriptionRepository
                .findActiveByTenantId(tenant.getId())
                .orElse(null);
        Plan plan = subscription != null ? subscription.getPlan() : null;

        // ── Plan features ──────────────────────────────────────────────────
        List<PlanFeature> planFeatures = plan != null
                ? planFeatureRepository.findByIdPlanId(plan.getId())
                : List.of();

        // ── Settings (merged: app → tenant → user) ─────────────────────────
        Map<String, String> settings = settingsResolver.merged(tenant.getId(), authUserId);

        // ── Effective privileges, then the menu they unlock ────────────────
        Set<String> privileges = menuResolutionService.effectivePrivileges(user);

        MenuResolutionService.MenuContext menuContext = new MenuResolutionService.MenuContext(
                tenant,
                user,
                applicationCode,
                plan != null ? plan.getCode().name() : "free",
                enabledFeatureCodes(planFeatures),
                settings.getOrDefault("locale", "en"));

        // One resolution feeds both: the settings hub is the SETTINGS-placed part of the same tree.
        List<MenuEntry> menus = menuResolutionService.resolve(menuContext);

        // ── Build response ─────────────────────────────────────────────────
        return BootstrapResponse.builder()
                .platform(buildPlatform())
                .user(buildUser(user, primaryRoles.code(tenant.getId(), authUserId), settings, privileges))
                .tenant(buildTenant(tenant))
                .branding(buildBranding(tenant, settings))
                .theme(buildTheme(settings))
                .features(buildFeatures(planFeatures))
                .subscription(buildSubscription(subscription, plan, planFeatures))
                .menus(menus)
                .settingsMenu(MenuTreeBuilder.forPlacement(menus, MenuPlacement.SETTINGS.name()))
                .notifications(NotificationsInfo.builder().unreadCount(0).build())
                .integrations(buildIntegrations(planFeatures))
                .environment(buildEnvironment())
                .build();
    }

    private static Set<String> enabledFeatureCodes(List<PlanFeature> planFeatures) {
        return planFeatures.stream()
                .filter(pf -> Boolean.TRUE.equals(pf.getEnabled()))
                .map(pf -> pf.getFeature().getCode())
                .collect(Collectors.toSet());
    }

    // ── Platform ───────────────────────────────────────────────────────────

    private PlatformInfo buildPlatform() {
        AppProperties.PlatformProperties p = appProperties.getPlatform();
        return PlatformInfo.builder()
                .name(p.getName())
                .version(appProperties.getVersion())
                .environment(appProperties.getEnvironment().getName())
                .supportUrl(p.getSupportUrl())
                .documentationUrl(p.getDocumentationUrl())
                .build();
    }

    // ── User ───────────────────────────────────────────────────────────────

    private UserInfo buildUser(TenantUser user, String primaryRole, Map<String, String> settings, Set<String> privileges) {
        String fullName = trim(user.getFirstName()) + " " + trim(user.getLastName());
        return UserInfo.builder()
                .id(user.getAuthUserId().toString())
                .name(fullName.isBlank() ? user.getEmail() : fullName.trim())
                .email(user.getEmail())
                .avatar("/avatars/" + user.getAuthUserId())
                .role(primaryRole == null ? null : primaryRole.toUpperCase())
                .permissions(privileges.stream().sorted().toList())
                .preferences(buildPreferences(settings))
                .build();
    }

    private static Map<String, String> buildPreferences(Map<String, String> settings) {
        Map<String, String> prefs = new LinkedHashMap<>();
        prefs.put("theme",    settings.getOrDefault("theme.mode", "light"));
        prefs.put("language", settings.getOrDefault("locale", "en").split("[-_]")[0]);
        return prefs;
    }

    // ── Tenant ─────────────────────────────────────────────────────────────

    private TenantInfo buildTenant(Tenant tenant) {
        String domain = tenant.getSlug() + "." + appProperties.getPlatform().getDomain();
        return TenantInfo.builder()
                .id(tenant.getTenantId())
                .name(tenant.getDisplayName())
                .slug(tenant.getSlug())
                .domain(domain)
                .plan(tenant.getPlan().name())
                .status(tenant.getStatus().name())
                .build();
    }

    // ── Branding ───────────────────────────────────────────────────────────

    private BrandingInfo buildBranding(Tenant tenant, Map<String, String> settings) {
        String slug = tenant.getSlug();
        AppProperties.PlatformProperties p = appProperties.getPlatform();

        String logo    = "/branding/" + slug + "/logo.png";
        String favicon = "/branding/" + slug + "/favicon.ico";

        // Resolved logo URL: tenant setting → platform config → tenant-relative path
        String platformLogo    = p.getLogoUrl()    != null ? p.getLogoUrl()    : logo;
        String platformFavicon = p.getFaviconUrl() != null ? p.getFaviconUrl() : favicon;
        String logoUrl    = settings.getOrDefault("branding.logoUrl",    platformLogo);
        String faviconUrl = settings.getOrDefault("branding.faviconUrl", platformFavicon);

        return BrandingInfo.builder()
                .logo(logo)
                .logoUrl(logoUrl)
                .favicon(favicon)
                .faviconUrl(faviconUrl)
                .primaryColor(settings.getOrDefault("theme.primaryColor", "#3ecf8e"))
                .secondaryColor(settings.get("theme.secondaryColor"))
                .font(settings.getOrDefault("theme.font", "Inter"))
                .build();
    }

    // ── Theme ──────────────────────────────────────────────────────────────

    private static ThemeInfo buildTheme(Map<String, String> settings) {
        return ThemeInfo.builder()
                .mode(settings.getOrDefault("theme.mode", "light"))
                .primaryColor(settings.getOrDefault("theme.primaryColor", "#3ecf8e"))
                .layout(settings.getOrDefault("theme.layout", "default"))
                .build();
    }

    // ── Features ───────────────────────────────────────────────────────────

    private static Map<String, Boolean> buildFeatures(List<PlanFeature> planFeatures) {
        return planFeatures.stream()
                .collect(Collectors.toMap(
                        pf -> pf.getFeature().getCode(),
                        pf -> Boolean.TRUE.equals(pf.getEnabled()),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }

    // ── Subscription ───────────────────────────────────────────────────────

    private static SubscriptionInfo buildSubscription(TenantSubscription sub,
                                                       Plan plan,
                                                       List<PlanFeature> planFeatures) {
        if (plan == null) return null;

        Long maxUsers    = limitOf(planFeatures, "users");
        Long maxProjects = limitOf(planFeatures, "doc_upload");

        List<String> enabledFeatureCodes = planFeatures.stream()
                .filter(pf -> Boolean.TRUE.equals(pf.getEnabled()))
                .map(pf -> pf.getFeature().getCode())
                .toList();

        LocalDate renewalDate = (sub != null && sub.getCurrentPeriodEnd() != null)
                ? sub.getCurrentPeriodEnd().atZone(ZoneOffset.UTC).toLocalDate()
                : null;

        return SubscriptionInfo.builder()
                .plan(plan.getCode().name())
                .maxUsers(maxUsers)
                .maxProjects(maxProjects)
                .features(enabledFeatureCodes)
                .renewalDate(renewalDate)
                .build();
    }

    private static Long limitOf(List<PlanFeature> planFeatures, String featureCode) {
        return planFeatures.stream()
                .filter(pf -> featureCode.equals(pf.getFeature().getCode()))
                .map(PlanFeature::getUsageLimit)
                .findFirst()
                .orElse(null);
    }

    // ── Integrations ───────────────────────────────────────────────────────

    private static IntegrationsInfo buildIntegrations(List<PlanFeature> planFeatures) {
        List<String> enabled = planFeatures.stream()
                .filter(pf -> Boolean.TRUE.equals(pf.getEnabled())
                        && FEATURE_TO_INTEGRATION.containsKey(pf.getFeature().getCode()))
                .map(pf -> FEATURE_TO_INTEGRATION.get(pf.getFeature().getCode()))
                .toList();
        return IntegrationsInfo.builder().enabled(enabled).build();
    }

    // ── Environment ────────────────────────────────────────────────────────

    private EnvironmentInfo buildEnvironment() {
        AppProperties.EnvironmentProperties env = appProperties.getEnvironment();
        return EnvironmentInfo.builder()
                .region(env.getRegion())
                .deployment(env.getDeployment())
                .buildNumber(env.getBuildNumber())
                .build();
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
