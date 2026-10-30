package com.flowbinary.platformservice.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Everything shared by all users of one (application, tenant): the live item
 * tree and the tenant's overrides. This is what the cache holds. Nothing here
 * depends on who is asking — privileges and user overrides are read per request.
 *
 * <p>Temporary menus that are scheduled or active are included whatever their
 * window, because {@link MenuVisibilityPolicy} checks the window against the
 * current time on each request; the cache therefore never needs to expire at a
 * start or end boundary.
 *
 * @param globalVersion version of the platform-wide scope this was built from
 * @param tenantVersion version of the tenant scope this was built from
 */
public record MenuCatalogue(List<CatalogItem> items,
                            Map<UUID, CatalogOverride> overrides,
                            long globalVersion,
                            long tenantVersion,
                            Instant builtAt) {
}
