package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.dto.menu.DraftResponse;
import com.flowbinary.platformservice.dto.menu.PublishResponse;
import com.flowbinary.platformservice.dto.menu.VersionResponse;
import com.flowbinary.platformservice.exception.MenuConflictException;
import com.flowbinary.platformservice.exception.MenuDraftException;
import com.flowbinary.platformservice.model.*;
import com.flowbinary.platformservice.repository.MenuChangeSetRepository;
import com.flowbinary.platformservice.repository.MenuSnapshotRepository;
import com.flowbinary.platformservice.service.MenuAdminAccess.AdminContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.*;

/**
 * Publishes a scope's draft: applies it to the live rows, records an immutable
 * snapshot, and audits every resulting change — all in one transaction.
 * Rollback never rewrites history: it opens a new draft that returns the scope
 * to an earlier snapshot, which is then previewed and published like any other.
 */
@Service
@RequiredArgsConstructor
public class MenuPublishService {

    private final MenuChangeSetRepository    changeSets;
    private final MenuSnapshotRepository     snapshots;
    private final MenuScopeLoader            loader;
    private final MenuChangeApplier          applier;
    private final MenuHierarchyValidator     hierarchy;
    private final AuditService               audit;
    private final MenuCatalogEvents          events;
    private final MenuMetrics                metrics;
    private final PlatformTransactionManager transactionManager;

    @Transactional
    public PublishResponse publish(AdminContext ctx, String note) {
        ctx.require(MenuAdminAccess.PUBLISH);
        MenuScope scope = ctx.scope();

        MenuChangeSet draft = (scope.isGlobal()
                ? changeSets.findOpenGlobal(scope.application().getId())
                : changeSets.findOpenForTenant(scope.application().getId(), scope.tenantId()))
                .orElseThrow(() -> new NoSuchElementException("No open draft"));

        int current = loader.currentVersion(scope);
        if (draft.getBaseVersion() != current) {
            throw new MenuConflictException("The menu was published (v" + current + ") after this draft began (v"
                    + draft.getBaseVersion() + "); rebase the draft first");
        }

        MenuChangeApplier.Workspace ws = loader.load(scope, ctx.actorId());
        Map<String, Object> before = loader.capture(ws);

        applier.apply(ws, draft.getOps());
        loader.persist(ws);

        List<String> problems = hierarchy.audit(ws.live());
        if (!problems.isEmpty()) {
            throw new MenuDraftException(-1, "Resulting menu is invalid: " + String.join("; ", problems));
        }

        Map<String, Object> after = loader.capture(ws);
        List<MenuScopeState.Change> changes = MenuScopeState.diff(before, after);
        if (changes.isEmpty()) throw new MenuDraftException(-1, "The draft makes no changes");

        MenuSnapshot snapshot = snapshots.save(MenuSnapshot.builder()
                .application(scope.application())
                .tenant(scope.tenant())
                .version(current + 1)
                .state(MenuJson.toNode(after))
                .changeSetId(draft.getId())
                .note(note)
                .publishedBy(ctx.actorId())
                .build());

        draft.setStatus(MenuChangeSetStatus.PUBLISHED);
        draft.setPublishedBy(ctx.actorId());
        draft.setPublishedAt(Instant.now());
        draft.setSnapshotId(snapshot.getId());
        draft.setUpdatedBy(ctx.actorId());

        for (MenuScopeState.Change c : changes) {
            audit.record(new AuditService.Entry(scope.tenantId(), ctx.actorId(),
                    c.kind().name(), entityType(c.key()), entityId(c.key()), c.before(), c.after(), note, "API"));
        }
        audit.record(new AuditService.Entry(scope.tenantId(), ctx.actorId(), "MENU_PUBLISHED", "MENU_SCOPE",
                scope.label(), Map.of("version", current),
                Map.of("version", current + 1, "changes", changes.size(), "changeSetId", draft.getId().toString()),
                note, "API"));

        events.changed(scope.application().getCode(), scope.tenantId());
        metrics.count("menu.publish", "scope", scope.isGlobal() ? "global" : "tenant");

        return new PublishResponse(current + 1, changes.size(), snapshot.getId());
    }

    /** Opens a draft that restores {@code toVersion}; nothing changes until it is published. */
    public DraftResponse rollback(AdminContext ctx, int toVersion) {
        ctx.require(MenuAdminAccess.PUBLISH);
        MenuScope scope = ctx.scope();

        MenuSnapshot target = loader.snapshots(scope).stream()
                .filter(s -> s.getVersion() == toVersion).findFirst()
                .orElseThrow(() -> new NoSuchElementException("No version " + toVersion));

        List<MenuOp> ops = tx().execute(s -> {
            if (changeSets.findOpenGlobalOrTenant(scope).isPresent()) {
                throw new MenuConflictException("Publish or discard the open draft before rolling back");
            }
            MenuChangeApplier.Workspace ws = loader.load(scope, ctx.actorId());
            return MenuScopeState.opsToReach(loader.capture(ws), target.stateMap());
        });
        if (ops.isEmpty()) throw new MenuDraftException(-1, "The menu already matches version " + toVersion);

        return tx().execute(s -> {
            MenuChangeSet draft = changeSets.saveAndFlush(MenuChangeSet.builder()
                    .application(scope.application())
                    .tenant(scope.tenant())
                    .title("Rollback to v" + toVersion)
                    .ops(new ArrayList<>(ops))
                    .baseVersion(loader.currentVersion(scope))
                    .createdBy(ctx.actorId())
                    .updatedBy(ctx.actorId())
                    .build());
            audit.record(new AuditService.Entry(scope.tenantId(), ctx.actorId(), "MENU_ROLLBACK_PREPARED",
                    "MENU_SCOPE", scope.label(), null, Map.of("toVersion", toVersion, "ops", ops.size()), null, "API"));
            return DraftResponse.of(draft, loader.currentVersion(scope));
        });
    }

    @Transactional(readOnly = true)
    public List<VersionResponse> versions(AdminContext ctx) {
        ctx.require(MenuAdminAccess.MANAGE);
        return loader.snapshots(ctx.scope()).stream().map(VersionResponse::of).toList();
    }

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    private static String entityType(String key) {
        return switch (key.substring(0, key.indexOf(':'))) {
            case "item"     -> "MENU_ITEM";
            case "grants"   -> "MENU_ROLE_GRANT";
            case "override" -> "MENU_TENANT_OVERRIDE";
            case "group"    -> "MENU_GROUP_ACCESS";
            default         -> "MENU_USER_ACCESS";
        };
    }

    private static String entityId(String key) {
        return key.substring(key.indexOf(':') + 1);
    }
}
