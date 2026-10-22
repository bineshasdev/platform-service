package com.flowbinary.platformservice.dto.menu;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** One row of the admin catalogue. {@code editable} is false for platform items seen from a tenant scope. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CatalogueItem(String code, String parentCode, String label, String icon, String route,
                            String type, String behavior, String target, int sortOrder, int depth,
                            boolean enabled, boolean visible, boolean editable,
                            String requiredPrivilege, String requiredFeature,
                            List<String> roles, List<String> plans, Override override) {

    public record Override(String label, String icon, Integer sortOrder, boolean hidden) {
    }
}
