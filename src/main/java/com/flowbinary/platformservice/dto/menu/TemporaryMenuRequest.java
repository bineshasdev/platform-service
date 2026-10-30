package com.flowbinary.platformservice.dto.menu;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * A time-bound menu. Times are UTC instants (ISO-8601, e.g. {@code 2026-11-01T09:00:00Z});
 * {@code startsAt} null means "now", {@code endsAt} is exclusive.
 * {@code code} is read on create only.
 */
public record TemporaryMenuRequest(
        String code,
        @NotBlank @Size(max = 100) String label,
        @Size(max = 500) String description,
        String icon,
        String route,
        /** LINK (default) or EXTERNAL. */
        String type,
        String target,
        /** Any visible parent in the caller's tree, or null for the root. */
        String parentCode,
        Integer sortOrder,
        /** Orders temporary menus that share a sort order; higher first. */
        Integer priority,
        @Size(max = 30) String badge,
        Instant startsAt,
        @NotNull Instant endsAt,
        String requiredPrivilege,
        String requiredFeature,
        Set<String> plans,
        Audience audience) {

    /**
     * {@code ALL_ELIGIBLE}: everyone who passes the plan, feature and privilege gates.
     * {@code RESTRICTED} (default): only the listed users. To reach a kind of person, use
     * {@code ALL_ELIGIBLE} with a required privilege. Users are tenant-scoped, so only a
     * tenant-scope menu may list them.
     */
    public record Audience(String mode, Set<UUID> users) {
    }
}
