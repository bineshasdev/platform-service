package com.flowbinary.platformservice.dto.module;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Registers or updates a module. {@code code} and {@code ownerClientId} are
 * fixed at registration; to change the owning client, deregister and register again.
 */
public record ModuleRequest(
        String code,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description,
        /** Application the module contributes to; defaults to PROVIDER_CONSOLE. */
        String application,
        /** Keycloak client whose client-credentials token may push the manifest. */
        String ownerClientId,
        /** Optional. Enables admin refresh and the slow scheduled pull. */
        String manifestUrl,
        /** Every route a module item uses must sit under one of these path prefixes. */
        List<String> allowedRoutePrefixes,
        /** privileges.module_code the module may reference besides shared platform privileges. */
        String privilegeModuleCode) {
}
