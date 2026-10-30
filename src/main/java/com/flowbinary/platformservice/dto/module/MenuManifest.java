package com.flowbinary.platformservice.dto.module;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * The contract a module publishes to describe its menu (schema version 1).
 *
 * <pre>
 * {
 *   "schemaVersion": 1,
 *   "module": "billing",
 *   "version": "2026.10.3",
 *   "items": [
 *     { "key": "root", "label": "Billing", "type": "GROUP", "icon": "CreditCard", "order": 50 },
 *     { "key": "invoices", "parent": "root", "label": "Invoices", "route": "/billing/invoices",
 *       "requiredPrivilege": "invoice.view", "order": 1,
 *       "translations": { "hi": "इनवॉइस" } }
 *   ]
 * }
 * </pre>
 * Unknown fields are rejected, so a typo cannot silently drop a restriction.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MenuManifest(int schemaVersion, String module, String version, List<Item> items) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = false)
    public record Item(
            /** Unique within the manifest; the platform item code is {@code <module>.<key>}. */
            String key,
            /** Key of another item in this manifest; null = a root of the module's menu. */
            String parent,
            String label,
            String description,
            String icon,
            /** In-app path under one of the module's registered prefixes. Required for LINK. */
            String route,
            /** LINK (default), GROUP or DIVIDER. Modules may not declare EXTERNAL links. */
            String type,
            String behavior,
            String target,
            /** Where the item renders: LEFT (default), HEADER, QUICK, USER, SETTINGS, TAB, INNER or TILE. */
            String placement,
            /** Tenant types the item applies to (CLINIC, LAB, STORE); null or empty = every type. */
            List<String> tenantTypes,
            /** Initial position among siblings. An administrator's later re-ordering is kept. */
            Integer order,
            Boolean separatorBefore,
            /** Required on every LINK: it is the only thing that decides who sees the item. */
            String requiredPrivilege,
            String requiredFeature,
            /** locale -> label. */
            Map<String, String> translations) {
    }
}
