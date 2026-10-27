package com.flowbinary.platformservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * One operation of a menu draft. A flat record keeps the stored JSON and the
 * API payload identical; which fields matter depends on {@link #op}.
 * Items are addressed by {@code code}, never by id, so a draft stays valid
 * across environments and rollbacks.
 */
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record MenuOp(
        MenuOpType   op,
        String       code,
        // structure
        String       parentCode,
        Integer      sortOrder,
        List<String> orderedCodes,
        // content
        String       label,
        String       description,
        String       icon,
        String       route,
        String       type,
        String       behavior,
        String       target,
        /** {@link MenuPlacement} name; PUT defaults to LEFT. */
        String       placement,
        Boolean      separatorBefore,
        Boolean      visible,
        Boolean      enabled,
        String       requiredPrivilege,
        String       requiredFeature,
        Set<String>  plans,
        /** {@link TenantType} names; global items only. PUT: null or empty = every type. PATCH: empty clears the gate. */
        Set<String>  tenantTypes,
        // assignment
        Set<String>  roles,
        UUID         userId,
        /** SET_GROUP_ACCESS: the tenant's group code. */
        String       group,
        String       effect,
        String       reason,
        Instant      validTo,
        // tenant override
        Boolean      hidden) {
}
