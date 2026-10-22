package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.model.Privilege;
import com.flowbinary.platformservice.model.Role;
import com.flowbinary.platformservice.model.TenantUser;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * The lookups a draft operation needs beyond the menu tree itself. A seam so
 * {@link MenuChangeApplier} runs in unit tests without a database.
 */
public interface MenuReferences {

    /** A role the scope may grant: global scope — system roles; tenant scope — system roles plus the tenant's own. */
    Optional<Role> assignableRole(MenuScope scope, String code);

    Optional<Privilege> privilege(String code);

    boolean featureExists(String code);

    /** The user, only if they belong to {@code tenantId}. */
    Optional<TenantUser> tenantUser(UUID tenantId, UUID userId);

    /** True when some tenant already owns an item with this code in the application. */
    boolean codeUsedByAnyTenant(UUID applicationId, String code);

    /** True when any of the given items has a tenant-owned child. */
    boolean hasTenantOwnedChildren(Collection<UUID> parentIds);
}
