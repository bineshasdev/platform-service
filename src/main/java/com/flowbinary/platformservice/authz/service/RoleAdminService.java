package com.flowbinary.platformservice.authz.service;

import com.flowbinary.platformservice.authz.model.*;
import com.flowbinary.platformservice.authz.repository.AuthzRoleRepository;
import com.flowbinary.platformservice.authz.repository.RoleAssignmentRepository;
import com.flowbinary.platformservice.authz.repository.RoleTemplateRepository;
import com.flowbinary.platformservice.exception.AuthzRuleException;
import com.flowbinary.platformservice.exception.AuthzRuleException.Rule;
import com.flowbinary.platformservice.model.Tenant;
import com.flowbinary.platformservice.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;

/**
 * A tenant's roles: read the templates of its type, derive roles from them,
 * build custom roles, edit and delete them. Every write runs the guardrails
 * and announces the change.
 */
@Service
@RequiredArgsConstructor
public class RoleAdminService {

    private static final Pattern CODE = Pattern.compile("^[a-z][a-z0-9_]{0,62}$");

    /** A new role: derived when {@code templateId} is set, custom otherwise. */
    public record CreateRole(String code, String name, String description, UUID templateId,
                             Map<String, DeltaEffect> deltas) {
    }

    /** Changes to a role; null fields are left as they are. {@code deltas} replaces the whole set. */
    public record UpdateRole(String name, String description, Map<String, DeltaEffect> deltas) {
    }

    private final RoleTemplateRepository templates;
    private final AuthzRoleRepository roles;
    private final RoleAssignmentRepository assignments;
    private final EffectivePermissionService permissions;
    private final TenantRoleProvisioner provisioner;
    private final TenantFeatures features;
    private final AuthzGuardrails guardrails;
    private final AuthzEvents events;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public List<RoleTemplate> templates(Tenant tenant) {
        return templates.findByTenantTypeOrderBySortOrderAsc(EffectivePermissionService.tenantType(tenant));
    }

    /** Privileges a role of this tenant may contain, for the role editor. */
    @Transactional(readOnly = true)
    public List<AuthzPrivilege> assignablePrivileges(Tenant tenant) {
        String type = EffectivePermissionService.tenantType(tenant);
        Set<String> enabled = features.enabled(tenant);
        return permissions.catalogue().values().stream()
                .filter(p -> p.usableIn(type) && p.isActive())
                .filter(p -> p.getFeatureCode() == null || enabled.contains(p.getFeatureCode()))
                .sorted(Comparator.comparing(AuthzPrivilege::getCode))
                .toList();
    }

    @Transactional
    public List<AuthzRole> roles(Tenant tenant) {
        provisioner.provision(tenant);   // a tenant created before its type had templates gets them now
        return roles.findByTenantIdOrderByNameAsc(tenant.getId());
    }

    @Transactional(readOnly = true)
    public long holders(UUID roleId) {
        return assignments.countByRoleId(roleId);
    }

    @Transactional
    public AuthzRole create(Tenant tenant, CreateRole req, PermissionSet editor, UUID actor) {
        if (req.code() == null || !CODE.matcher(req.code()).matches()) {
            throw new IllegalArgumentException("code must be 1-63 chars of lowercase letters, digits and '_', starting with a letter");
        }
        if (req.name() == null || req.name().isBlank()) throw new IllegalArgumentException("name is required");
        if (roles.existsByTenantIdAndCode(tenant.getId(), req.code())) {
            throw new AuthzRuleException(Rule.DUPLICATE_CODE, "A role with code '" + req.code() + "' already exists");
        }

        RoleKind kind = req.templateId() == null ? RoleKind.CUSTOM : RoleKind.DERIVED;
        guardrails.entitled(tenant, kind);
        if (kind == RoleKind.DERIVED) {
            RoleTemplate t = templates.findById(req.templateId())
                    .filter(x -> x.getTenantType().equals(EffectivePermissionService.tenantType(tenant)))
                    .orElseThrow(() -> new IllegalArgumentException("Template not found for this tenant type"));
            if (!t.isPrimaryEligible()) throw new IllegalArgumentException("Template cannot be derived from");
        }
        Map<String, DeltaEffect> deltas = req.deltas() == null ? Map.of() : req.deltas();
        guardrails.privilegesUsableIn(tenant, deltas.keySet(), permissions.catalogue());
        // Nobody holds a new role, so creating one cannot escalate anyone; assigning it is checked then.

        AuthzRole role = new AuthzRole();
        role.setTenantId(tenant.getId());
        role.setTemplateId(req.templateId());
        role.setKind(kind);
        role.setCode(req.code());
        role.setName(req.name().trim());
        role.setDescription(req.description());
        role.setDeltas(new HashMap<>(deltas));
        role.setCreatedBy(actor);
        role.setUpdatedBy(actor);
        role = roles.save(role);

        audit.record(new AuditService.Entry(tenant.getId(), actor, "AUTHZ_ROLE_CREATED", "authz_role",
                role.getId().toString(), null, describe(role), null, "API"));
        // Nobody holds a new role yet, so nobody's access changed.
        return role;
    }

    @Transactional
    public AuthzRole update(Tenant tenant, UUID roleId, UpdateRole req, PermissionSet editor, UUID actor) {
        AuthzRole role = editable(tenant, roleId);
        Map<String, Object> before = describe(role);

        if (req.name() != null) {
            if (req.name().isBlank()) throw new IllegalArgumentException("name cannot be blank");
            role.setName(req.name().trim());
        }
        if (req.description() != null) role.setDescription(req.description());
        if (req.deltas() != null) {
            guardrails.privilegesUsableIn(tenant, req.deltas().keySet(), permissions.catalogue());
            // Editing a role you hold changes your own access: only within what you already hold.
            if (editor.roles().contains(role.getCode())) {
                Set<String> touched = new HashSet<>();
                Map<String, DeltaEffect> old = role.getDeltas();
                for (String code : union(old.keySet(), req.deltas().keySet())) {
                    if (!Objects.equals(old.get(code), req.deltas().get(code))) touched.add(code);
                }
                guardrails.noSelfEscalation(editor, touched);
            }
            role.getDeltas().clear();
            role.getDeltas().putAll(req.deltas());
        }
        role.setUpdatedBy(actor);
        role = roles.save(role);

        guardrails.someoneStillAdministers(tenant);
        audit.record(new AuditService.Entry(tenant.getId(), actor, "AUTHZ_ROLE_UPDATED", "authz_role",
                role.getId().toString(), before, describe(role), null, "API"));
        events.tenantChanged(tenant.getId(), "ROLE_CHANGED");
        return role;
    }

    @Transactional
    public void delete(Tenant tenant, UUID roleId, UUID actor) {
        AuthzRole role = editable(tenant, roleId);
        if (assignments.existsByRoleId(roleId)) {
            throw new AuthzRuleException(Rule.ROLE_IN_USE, "Role '" + role.getCode() + "' is assigned; reassign its holders first");
        }
        Map<String, Object> before = describe(role);
        roles.delete(role);
        audit.record(new AuditService.Entry(tenant.getId(), actor, "AUTHZ_ROLE_DELETED", "authz_role",
                roleId.toString(), before, null, null, "API"));
    }

    /** A derived or custom role of this tenant whose kind the plan lets them edit. */
    private AuthzRole editable(Tenant tenant, UUID roleId) {
        AuthzRole role = roles.findById(roleId)
                .filter(r -> tenant.getId().equals(r.getTenantId()))
                .orElseThrow(() -> new NoSuchElementException("Role not found: " + roleId));
        if (role.getKind() == RoleKind.TEMPLATE) {
            throw new AuthzRuleException(Rule.TEMPLATE_IMMUTABLE, "Template roles cannot be edited; create a role derived from it");
        }
        guardrails.entitled(tenant, role.getKind());
        return role;
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> out = new HashSet<>(a);
        out.addAll(b);
        return out;
    }

    private static Map<String, Object> describe(AuthzRole role) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", role.getCode());
        m.put("name", role.getName());
        m.put("kind", role.getKind().name());
        if (role.getTemplateId() != null) m.put("templateId", role.getTemplateId().toString());
        Map<String, String> deltas = new TreeMap<>();
        role.getDeltas().forEach((k, v) -> deltas.put(k, v.name()));
        m.put("deltas", deltas);
        return m;
    }
}
