package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.authz.model.AuthzRole;
import com.flowbinary.platformservice.authz.repository.AuthzRoleRepository;
import com.flowbinary.platformservice.dto.menu.PreviewRequest;
import com.flowbinary.platformservice.dto.menu.PreviewResponse;
import com.flowbinary.platformservice.model.*;
import com.flowbinary.platformservice.repository.MenuChangeSetRepository;
import com.flowbinary.platformservice.repository.TenantRepository;
import com.flowbinary.platformservice.repository.TenantUserRepository;
import com.flowbinary.platformservice.service.MenuAdminAccess.AdminContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Shows what a user, or anyone holding a role, would see — with the open draft
 * applied or without it. The draft goes through {@link MenuChangeApplier} on
 * the real rows inside a transaction that is always rolled back, so the preview
 * runs the same code that publish will run.
 *
 * <p>Isolation: a tenant administrator may only preview users of their own
 * tenant. The target user is looked up inside the evaluation tenant, so a
 * foreign user id behaves exactly like an unknown one.
 */
@Service
@RequiredArgsConstructor
public class MenuPreviewService {

    private final MenuScopeLoader           loader;
    private final MenuChangeApplier         applier;
    private final MenuChangeSetRepository   changeSets;
    private final MenuResolutionService     resolution;
    private final MenuQueryService          queryService;
    private final SettingsResolver          settings;
    private final AuthzRoleRepository       authzRoles;
    private final TenantRepository          tenantRepository;
    private final TenantUserRepository      tenantUserRepository;

    public PreviewResponse preview(AdminContext ctx, PreviewRequest req) {
        ctx.require(MenuAdminAccess.MANAGE);
        if ((req.userId() == null) == (req.roleCode() == null)) {
            throw new IllegalArgumentException("Give exactly one of userId or roleCode");
        }
        Tenant evalTenant = evaluationTenant(ctx, req);
        MenuScope scope = ctx.scope();

        MenuChangeSet draft = req.draft()
                ? changeSets.findOpenGlobalOrTenant(scope).orElse(null)
                : null;
        boolean usedDraft = draft != null;

        var menu = loader.inRollback(() -> {
            if (usedDraft) {
                MenuChangeApplier.Workspace ws = loader.load(scope, ctx.actorId());
                applier.apply(ws, draft.getOps());
                loader.persist(ws);
            }
            MenuQueryService.PlanContext plan = queryService.planContext(evalTenant);

            if (req.userId() != null) {
                TenantUser user = tenantUserRepository.findById(req.userId())
                        .filter(u -> u.getTenant().getId().equals(evalTenant.getId()))
                        .orElseThrow(() -> new NoSuchElementException("User not found"));
                String locale = settings.merged(evalTenant.getId(), user.getAuthUserId()).getOrDefault("locale", "en");
                return resolution.resolveUncached(new MenuResolutionService.MenuContext(
                        evalTenant, user, scope.application().getCode(), plan.planCode(), plan.features(), locale));
            }
            AuthzRole role = authzRoles.findByTenantIdAndCode(evalTenant.getId(), req.roleCode())
                    .filter(AuthzRole::isActive)
                    .orElseThrow(() -> new NoSuchElementException("Role not found"));
            return resolution.resolveForRole(role, new MenuResolutionService.MenuContext(
                    evalTenant, null, scope.application().getCode(), plan.planCode(), plan.features(), "en"));
        });
        return new PreviewResponse(usedDraft, evalTenant.getId(), menu);
    }

    /**
     * Tenant scope: its own tenant. Global scope: the operator names a tenant to
     * evaluate against (plan, features and overrides are per tenant).
     */
    private Tenant evaluationTenant(AdminContext ctx, PreviewRequest req) {
        if (!ctx.scope().isGlobal()) return ctx.scope().tenant();
        if (req.tenantId() == null) {
            throw new IllegalArgumentException("tenantId is required to preview the global menu");
        }
        return tenantRepository.findById(req.tenantId())
                .orElseThrow(() -> new NoSuchElementException("Tenant not found"));
    }
}
