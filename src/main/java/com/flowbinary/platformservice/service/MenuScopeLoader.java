package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.model.*;
import com.flowbinary.platformservice.repository.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Loads, captures and persists the working set of one menu scope. */
@Component
@RequiredArgsConstructor
public class MenuScopeLoader {

    private final MenuItemRepository           menuItemRepository;
    private final MenuTenantOverrideRepository overrideRepository;
    private final MenuUserAccessRepository     userAccessRepository;
    private final MenuSnapshotRepository       snapshotRepository;
    private final EntityManager                entityManager;
    private final PlatformTransactionManager   transactionManager;

    public MenuChangeApplier.Workspace load(MenuScope scope, UUID actorId) {
        UUID appId = scope.application().getId();
        List<MenuItem> items = scope.isGlobal()
                ? menuItemRepository.findGlobalItems(appId)
                : menuItemRepository.findScopeItems(appId, scope.tenantId());
        List<MenuTenantOverride> overrides = scope.isGlobal() ? List.of()
                : overrideRepository.findByTenantIdAndMenuItemApplicationId(scope.tenantId(), appId);
        List<MenuUserAccess> access = scope.isGlobal() ? List.of()
                : userAccessRepository.findForTenantAndApplication(scope.tenantId(), appId);
        return new MenuChangeApplier.Workspace(scope, actorId, items, overrides, access);
    }

    public Map<String, Object> capture(MenuChangeApplier.Workspace ws) {
        return MenuScopeState.capture(ws.scope, ws.items, ws.overrides, ws.userAccess);
    }

    /** Writes what the applier created and removed, then flushes so queries see it. */
    public void persist(MenuChangeApplier.Workspace ws) {
        // Items first: assignments reference them.
        ws.created.stream().filter(MenuItem.class::isInstance).forEach(entityManager::persist);
        ws.created.stream().filter(o -> !(o instanceof MenuItem)).forEach(entityManager::persist);
        ws.removed.forEach(entityManager::remove);
        entityManager.flush();
    }

    /** Highest published version of the scope; 0 when only the baseline (or nothing) exists. */
    public int currentVersion(MenuScope scope) {
        List<MenuSnapshot> all = scope.isGlobal()
                ? snapshotRepository.findGlobal(scope.application().getId())
                : snapshotRepository.findForTenant(scope.application().getId(), scope.tenantId());
        return all.isEmpty() ? 0 : all.get(0).getVersion();
    }

    public List<MenuSnapshot> snapshots(MenuScope scope) {
        return scope.isGlobal()
                ? snapshotRepository.findGlobal(scope.application().getId())
                : snapshotRepository.findForTenant(scope.application().getId(), scope.tenantId());
    }

    /** Takes the version-0 snapshot the first time a scope is edited, so version 1 can be rolled back. */
    public void ensureBaseline(MenuScope scope, UUID actorId) {
        if (!snapshots(scope).isEmpty()) return;
        MenuChangeApplier.Workspace ws = load(scope, actorId);
        snapshotRepository.save(MenuSnapshot.builder()
                .application(scope.application())
                .tenant(scope.tenant())
                .version(0)
                .state(MenuJson.toNode(capture(ws)))
                .note("Baseline before first draft")
                .build());
    }

    /**
     * Runs {@code work} in a transaction that is always rolled back. Used by
     * preview and by draft validation: the real apply code runs against the
     * real rows, its result is read, and nothing is kept.
     */
    public <T> T inRollback(Supplier<T> work) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template.execute(status -> {
            try {
                return work.get();
            } finally {
                status.setRollbackOnly();
            }
        });
    }
}
