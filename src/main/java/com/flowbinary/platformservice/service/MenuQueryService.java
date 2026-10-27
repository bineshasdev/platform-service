package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.dto.bootstrap.MenuEntry;
import com.flowbinary.platformservice.model.*;
import com.flowbinary.platformservice.repository.PlanFeatureRepository;
import com.flowbinary.platformservice.repository.TenantSubscriptionRepository;
import com.flowbinary.platformservice.repository.TenantUserRepository;
import com.flowbinary.platformservice.security.TenantContextResolver;
import com.flowbinary.platformservice.security.TenantNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Resolves the menu for the authenticated caller on its own, for
 * {@code GET /api/v1/menus/me}. {@code /bootstrap} builds the same
 * {@link MenuResolutionService.MenuContext} from data it has already loaded,
 * so both paths run the identical resolver.
 */
@Service
@RequiredArgsConstructor
public class MenuQueryService {

    private final TenantContextResolver        tenantContextResolver;
    private final TenantUserRepository         tenantUserRepository;
    private final TenantSubscriptionRepository subscriptionRepository;
    private final PlanFeatureRepository        planFeatureRepository;
    private final SettingsResolver             settingsResolver;
    private final MenuResolutionService        menuResolutionService;

    @Transactional(readOnly = true)
    public List<MenuEntry> menuFor(Authentication authentication, String applicationCode) {
        return menuFor(authentication, applicationCode, null);
    }

    /** The caller's menu; with {@code placement}, only the subtrees that render there. */
    @Transactional(readOnly = true)
    public List<MenuEntry> menuFor(Authentication authentication, String applicationCode, MenuPlacement placement) {
        Jwt jwt = ((JwtAuthenticationToken) authentication).getToken();
        Tenant tenant = tenantContextResolver.resolveRequired(authentication);

        UUID authUserId = UUID.fromString(jwt.getSubject());
        TenantUser user = tenantUserRepository
                .findByTenantIdAndAuthUserId(tenant.getId(), authUserId)
                .orElseThrow(() -> new TenantNotFoundException("User not found in tenant: " + authUserId));

        PlanContext planContext = planContext(tenant);

        Map<String, String> settings = settingsResolver.merged(tenant.getId(), authUserId);

        List<MenuEntry> tree = menuResolutionService.resolve(new MenuResolutionService.MenuContext(
                tenant, user, applicationCode,
                planContext.planCode(),
                planContext.features(),
                settings.getOrDefault("locale", "en")));
        return placement == null ? tree : MenuTreeBuilder.forPlacement(tree, placement.name());
    }

    /** The plan code and enabled feature codes menu gating needs for a tenant. */
    public record PlanContext(String planCode, Set<String> features) {
    }

    @Transactional(readOnly = true)
    public PlanContext planContext(Tenant tenant) {
        Plan plan = subscriptionRepository.findActiveByTenantId(tenant.getId())
                .map(TenantSubscription::getPlan)
                .orElse(null);
        Set<String> features = plan == null ? Set.of()
                : planFeatureRepository.findByIdPlanId(plan.getId()).stream()
                        .filter(pf -> Boolean.TRUE.equals(pf.getEnabled()))
                        .map(pf -> pf.getFeature().getCode())
                        .collect(Collectors.toSet());
        return new PlanContext(plan != null ? plan.getCode().name() : "free", features);
    }
}
