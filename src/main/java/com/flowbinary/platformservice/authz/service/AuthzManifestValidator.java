package com.flowbinary.platformservice.authz.service;

import com.flowbinary.platformservice.authz.model.AuthzPrivilege;
import com.flowbinary.platformservice.authz.service.AuthzManifest.GrantDecl;
import com.flowbinary.platformservice.authz.service.AuthzManifest.PrivilegeDecl;

import java.util.*;
import java.util.regex.Pattern;

/**
 * The rules a manifest must satisfy (design §21.2), applied without touching the database so they can be
 * tested exhaustively. Every problem is reported, not just the first.
 */
public final class AuthzManifestValidator {

    public static final int MAX_PRIVILEGES = 500;
    public static final String PLATFORM = "platform";

    static final Pattern SERVICE = Pattern.compile("^[a-z][a-z0-9_]{1,31}$");
    static final Pattern CODE = Pattern.compile("^[a-z][a-z0-9_]*\\.[a-z][a-z0-9_]*\\.[a-z][a-z0-9_]*$");
    static final Set<String> TENANT_TYPES = Set.of("CLINIC", "LAB", "STORE");
    static final Set<String> SCOPES = Set.of("TENANT", "LOCATION", "SELF");
    static final List<String> RISKS = List.of("LOW", "HIGH", "CRITICAL");

    private AuthzManifestValidator() {
    }

    /** What the validator needs to know about the platform's current state. */
    public record Context(Map<String, AuthzPrivilege> ownPrivileges,
                          Set<String> featureCodes,
                          Set<String> templateKeys) {
        /** @param templateKeys entries of the form {@code CLINIC/doctor} */
        public Context {
            ownPrivileges = ownPrivileges == null ? Map.of() : ownPrivileges;
            featureCodes = featureCodes == null ? Set.of() : featureCodes;
            templateKeys = templateKeys == null ? Set.of() : templateKeys;
        }
    }

    public static List<String> validate(String service, AuthzManifest m, Context ctx) {
        List<String> errors = new ArrayList<>();
        if (m == null) return List.of("manifest is empty");

        if (m.service() == null || !m.service().equals(service)) {
            errors.add("service must be '" + service + "' to match the path");
        }
        if (!SERVICE.matcher(service).matches()) errors.add("service '" + service + "' is not a valid service name");
        if (PLATFORM.equals(service)) errors.add("'platform' is reserved: its privileges are managed by the platform itself");
        if (m.version() != null && m.version().length() > 64) errors.add("version is longer than 64 characters");

        List<PrivilegeDecl> privileges = m.privileges() == null ? List.of() : m.privileges();
        if (privileges.isEmpty()) errors.add("privileges must list at least one privilege");
        if (privileges.size() > MAX_PRIVILEGES) errors.add("more than " + MAX_PRIVILEGES + " privileges");

        Map<String, PrivilegeDecl> declared = new LinkedHashMap<>();
        for (int i = 0; i < Math.min(privileges.size(), MAX_PRIVILEGES); i++) {
            privilege(service, "privileges[" + i + "]", privileges.get(i), declared, ctx, errors);
        }
        grants(m.templateGrants() == null ? List.of() : m.templateGrants(), declared, ctx, errors);
        return errors;
    }

    private static void privilege(String service, String at, PrivilegeDecl p, Map<String, PrivilegeDecl> declared,
                                  Context ctx, List<String> errors) {
        if (p == null) {
            errors.add(at + " is empty");
            return;
        }
        if (p.code() == null || !CODE.matcher(p.code()).matches()) {
            errors.add(at + ".code must look like <service>.<resource>.<action>, lower case");
            return;
        }
        if (!p.code().startsWith(service + ".")) errors.add(at + ".code '" + p.code() + "' is outside the '" + service + "' namespace");
        if (declared.put(p.code(), p) != null) errors.add(at + ".code '" + p.code() + "' is declared twice");
        if (p.name() == null || p.name().isBlank() || p.name().length() > 100) errors.add(at + ".name is required (up to 100 characters)");
        if (p.description() != null && p.description().length() > 300) errors.add(at + ".description is longer than 300 characters");

        List<String> types = p.tenantTypes() == null ? List.of() : p.tenantTypes();
        if (types.isEmpty()) errors.add(at + ".tenantTypes must name at least one tenant type");
        for (String t : types) if (!TENANT_TYPES.contains(t)) errors.add(at + ".tenantTypes: unknown tenant type '" + t + "'");
        if (p.scope() != null && !SCOPES.contains(p.scope())) errors.add(at + ".scope must be TENANT, LOCATION or SELF");
        if (p.risk() != null && !RISKS.contains(p.risk())) errors.add(at + ".risk must be LOW, HIGH or CRITICAL");
        if (p.feature() != null && !p.feature().isBlank() && !ctx.featureCodes().contains(p.feature())) {
            errors.add(at + ".feature '" + p.feature() + "' does not exist");
        }
        loosening(at, p, ctx.ownPrivileges().get(p.code()), errors);
    }

    /** A role may rely on a privilege's flags and reach, so a manifest can only tighten the flags and widen the reach. */
    private static void loosening(String at, PrivilegeDecl p, AuthzPrivilege existing, List<String> errors) {
        if (existing == null) return;
        if (existing.isPhi() && !Boolean.TRUE.equals(p.phi())) errors.add(at + ".phi cannot be lowered once set");
        if (existing.isClinicalWrite() && !Boolean.TRUE.equals(p.clinicalWrite())) errors.add(at + ".clinicalWrite cannot be lowered once set");
        String risk = p.risk() == null ? "LOW" : p.risk();
        if (RISKS.indexOf(risk) >= 0 && RISKS.indexOf(risk) < RISKS.indexOf(existing.getRisk())) {
            errors.add(at + ".risk cannot be lowered from " + existing.getRisk());
        }
        List<String> types = p.tenantTypes() == null ? List.of() : p.tenantTypes();
        for (String t : existing.getTenantTypes()) {
            if (!types.contains(t)) errors.add(at + ".tenantTypes cannot drop " + t + ": roles of those tenants may hold it");
        }
    }

    private static void grants(List<GrantDecl> grants, Map<String, PrivilegeDecl> declared, Context ctx, List<String> errors) {
        for (int i = 0; i < grants.size(); i++) {
            GrantDecl g = grants.get(i);
            String at = "templateGrants[" + i + "]";
            if (g == null) {
                errors.add(at + " is empty");
                continue;
            }
            String key = g.tenantType() + "/" + g.template();
            if (!ctx.templateKeys().contains(key)) errors.add(at + ": no template '" + g.template() + "' for tenant type '" + g.tenantType() + "'");
            for (String code : g.privileges() == null ? List.<String>of() : g.privileges()) {
                PrivilegeDecl p = declared.get(code);
                if (p == null) errors.add(at + ": '" + code + "' is not declared in this manifest; a service can only propose its own privileges");
                else if (p.tenantTypes() != null && !p.tenantTypes().contains(g.tenantType())) {
                    errors.add(at + ": '" + code + "' does not exist for " + g.tenantType() + " tenants");
                }
            }
        }
    }
}
