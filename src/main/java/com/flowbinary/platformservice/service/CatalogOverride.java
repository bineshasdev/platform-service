package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.model.MenuTenantOverride;

/** Detached copy of a tenant's presentation override. Null fields inherit the master value. */
public record CatalogOverride(String label, String icon, Integer sortOrder, boolean hidden) {

    public static CatalogOverride of(MenuTenantOverride o) {
        return new CatalogOverride(o.getLabel(), o.getIcon(), o.getSortOrder(), Boolean.TRUE.equals(o.getIsHidden()));
    }
}
