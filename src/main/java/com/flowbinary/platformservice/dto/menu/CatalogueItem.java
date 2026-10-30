package com.flowbinary.platformservice.dto.menu;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** One row of the admin catalogue. {@code editable} is false for platform items seen from a tenant scope. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CatalogueItem(String code, String parentCode, String label, String icon, String route,
                            String type, String behavior, String target, String placement, int sortOrder, int depth,
                            boolean enabled, boolean visible, boolean editable,
                            String requiredPrivilege, String requiredFeature,
                            List<String> plans,
                            /** Tenant types the item applies to; null = every type. */
                            List<String> tenantTypes,
                            Override override,
                            String source, String lifecycleStatus,
                            java.time.Instant startsAt, java.time.Instant endsAt,
                            /** Code of the owning module for a module item; null otherwise. */
                            String module) {

    public record Override(String label, String icon, Integer sortOrder, boolean hidden) {
    }
}
