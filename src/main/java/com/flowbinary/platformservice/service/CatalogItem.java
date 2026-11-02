package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.model.*;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * An immutable, detached copy of a menu item holding exactly what resolution
 * needs. The cache stores these instead of JPA entities, so a cached catalogue
 * is thread-safe and never triggers a lazy load on a request thread.
 */
public record CatalogItem(UUID id,
                          UUID parentId,
                          String code,
                          String label,
                          String icon,
                          String route,
                          MenuItemType type,
                          MenuBehavior behavior,
                          MenuTarget target,
                          MenuPlacement placement,
                          int sortOrder,
                          int priority,
                          boolean separatorBefore,
                          boolean visible,
                          String requiredFeature,
                          String requiredPrivilege,
                          boolean temporary,
                          MenuSource source,
                          MenuLifecycle lifecycle,
                          Instant startsAt,
                          Instant endsAt,
                          MenuAudienceMode audience,
                          String badge,
                          Set<String> plans,
                          /** Tenant types the item applies to; empty = every type. */
                          Set<String> tenantTypes,
                          Map<String, String> translations) {

    /** Snapshot of an entity. Must run inside the transaction that loaded it. */
    public static CatalogItem of(MenuItem m) {
        return new CatalogItem(
                m.getId(),
                m.getParent() == null ? null : m.getParent().getId(),
                m.getCode(), m.getLabel(), m.getIcon(), m.getRoute(),
                m.getType(), m.getBehavior(), m.getTarget(), m.getPlacement(),
                m.getSortOrder(), m.getPriority(),
                Boolean.TRUE.equals(m.getSeparatorBefore()),
                Boolean.TRUE.equals(m.getIsVisible()),
                m.getRequiredFeature(),
                m.getRequiredPrivilege(),
                m.isTemporary(), m.getSource(), m.getLifecycleStatus(), m.getStartsAt(), m.getEndsAt(),
                m.getAudienceMode(), m.getBadge(),
                m.getPlanAccess().stream().map(p -> p.getId().getPlan().name().toLowerCase()).collect(Collectors.toUnmodifiableSet()),
                m.getTenantTypes() == null ? Set.of() : Set.copyOf(m.getTenantTypes()),
                m.getTranslations().stream().collect(Collectors.toUnmodifiableMap(
                        t -> t.getId().getLocale().toLowerCase(), MenuItemTranslation::getLabel, (a, b) -> a)));
    }

    /** True when a temporary menu may be shown at {@code at}: live status and inside its window. */
    public boolean isLiveAt(Instant at) {
        if (!temporary) return lifecycle == MenuLifecycle.ACTIVE;
        return lifecycle.isLiveCandidate()
                && startsAt != null && endsAt != null
                && !startsAt.isAfter(at) && endsAt.isAfter(at);
    }

    /** Label for a locale, falling back to the default label. */
    public String labelFor(String locale) {
        if (locale == null) return label;
        return translations.getOrDefault(locale.toLowerCase(), label);
    }
}
