package com.flowbinary.platformservice.authz.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * What a service tells the platform about the privileges it protects (design §10, §21.2).
 * Parsed strictly: an unknown field is an error, so a typo cannot silently drop a flag.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record AuthzManifest(String service,
                            String version,
                            List<PrivilegeDecl> privileges,
                            List<GrantDecl> templateGrants) {

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record PrivilegeDecl(String code,
                                String name,
                                String description,
                                List<String> tenantTypes,
                                String feature,
                                String scope,
                                Boolean phi,
                                Boolean clinicalWrite,
                                String risk) {
    }

    /** A proposal: these privileges should be part of one template. */
    @JsonIgnoreProperties(ignoreUnknown = false)
    public record GrantDecl(String tenantType, String template, List<String> privileges) {
    }
}
