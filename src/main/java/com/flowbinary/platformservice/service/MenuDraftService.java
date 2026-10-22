package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.dto.menu.DraftOpsRequest;
import com.flowbinary.platformservice.dto.menu.DraftResponse;
import com.flowbinary.platformservice.exception.MenuConflictException;
import com.flowbinary.platformservice.model.MenuChangeSet;
import com.flowbinary.platformservice.model.MenuChangeSetStatus;
import com.flowbinary.platformservice.model.MenuOp;
import com.flowbinary.platformservice.repository.MenuChangeSetRepository;
import com.flowbinary.platformservice.service.MenuAdminAccess.AdminContext;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * The open draft of a menu scope. Every change is dry-run against the real
 * rows (and rolled back) before it is stored, so a draft that is saved is one
 * that applies cleanly at the version it was started from.
 */
@Service
@RequiredArgsConstructor
public class MenuDraftService {

    private final MenuChangeSetRepository     changeSets;
    private final MenuScopeLoader             loader;
    private final MenuChangeApplier           applier;
    private final AuditService                audit;
    private final PlatformTransactionManager  transactionManager;

    @Transactional(readOnly = true)
    public Optional<DraftResponse> current(AdminContext ctx) {
        ctx.require(MenuAdminAccess.MANAGE);
        return findOpen(ctx.scope()).map(c -> DraftResponse.of(c, loader.currentVersion(ctx.scope())));
    }

    /** Adds operations to the draft, creating it if there is none. */
    public DraftResponse append(AdminContext ctx, DraftOpsRequest req) {
        authorise(ctx, req.ops());
        MenuChangeSet existing = findOpen(ctx.scope()).orElse(null);
        List<MenuOp> combined = new ArrayList<>();
        if (existing != null) combined.addAll(existing.getOps());
        combined.addAll(req.ops());
        return store(ctx, existing, req, combined, req.ops());
    }

    /** Replaces the whole operation list. */
    public DraftResponse replace(AdminContext ctx, DraftOpsRequest req) {
        authorise(ctx, req.ops());
        MenuChangeSet existing = findOpen(ctx.scope()).orElse(null);
        return store(ctx, existing, req, new ArrayList<>(req.ops()), req.ops());
    }

    @Transactional
    public void discard(AdminContext ctx) {
        ctx.require(MenuAdminAccess.MANAGE);
        MenuChangeSet draft = findOpen(ctx.scope())
                .orElseThrow(() -> new NoSuchElementException("No open draft"));
        draft.setStatus(MenuChangeSetStatus.DISCARDED);
        draft.setUpdatedBy(ctx.actorId());
        audit.record(new AuditService.Entry(ctx.scope().tenantId(), ctx.actorId(), "MENU_DRAFT_DISCARDED",
                "MENU_DRAFT", draft.getId().toString(), null,
                java.util.Map.of("opCount", draft.getOps().size()), null, "API"));
    }

    /** Moves the draft's base to the scope's current version, after proving its operations still apply. */
    public DraftResponse rebase(AdminContext ctx) {
        ctx.require(MenuAdminAccess.MANAGE);
        MenuChangeSet draft = findOpen(ctx.scope())
                .orElseThrow(() -> new NoSuchElementException("No open draft"));
        dryRun(ctx, draft.getOps());
        return tx().execute(s -> {
            MenuChangeSet d = changeSets.findById(draft.getId()).orElseThrow();
            d.setBaseVersion(loader.currentVersion(ctx.scope()));
            d.setUpdatedBy(ctx.actorId());
            audit.record(new AuditService.Entry(ctx.scope().tenantId(), ctx.actorId(), "MENU_DRAFT_REBASED",
                    "MENU_DRAFT", d.getId().toString(), null, java.util.Map.of("baseVersion", d.getBaseVersion()), null, "API"));
            return DraftResponse.of(changeSets.save(d), d.getBaseVersion());
        });
    }

    // ── internals ──────────────────────────────────────────────────────────

    private DraftResponse store(AdminContext ctx, MenuChangeSet existing, DraftOpsRequest req,
                                List<MenuOp> resulting, List<MenuOp> added) {
        if (existing != null && (req.expectedRowVersion() == null
                || !req.expectedRowVersion().equals(existing.getRowVersion()))) {
            throw new MenuConflictException("The draft changed (now at rowVersion " + existing.getRowVersion()
                    + "); reload it and retry");
        }
        dryRun(ctx, resulting);

        try {
            return tx().execute(s -> {
                MenuChangeSet draft;
                if (existing == null) {
                    loader.ensureBaseline(ctx.scope(), ctx.actorId());
                    draft = MenuChangeSet.builder()
                            .application(ctx.scope().application())
                            .tenant(ctx.scope().tenant())
                            .baseVersion(loader.currentVersion(ctx.scope()))
                            .createdBy(ctx.actorId())
                            .build();
                } else {
                    draft = changeSets.findById(existing.getId()).orElseThrow();
                }
                draft.setOps(new ArrayList<>(resulting));
                if (req.title() != null) draft.setTitle(req.title());
                draft.setUpdatedBy(ctx.actorId());
                draft = changeSets.saveAndFlush(draft);

                for (MenuOp op : added) {
                    audit.record(new AuditService.Entry(ctx.scope().tenantId(), ctx.actorId(), "MENU_DRAFT_OP_ADDED",
                            "MENU_DRAFT", draft.getId().toString(), null, op, null, "API"));
                }
                return DraftResponse.of(draft, loader.currentVersion(ctx.scope()));
            });
        } catch (OptimisticLockingFailureException | DataIntegrityViolationException e) {
            throw new MenuConflictException("Someone else changed this draft at the same time; reload and retry");
        }
    }

    /** Runs the real apply code against the real rows, then discards everything. */
    private void dryRun(AdminContext ctx, List<MenuOp> ops) {
        loader.inRollback(() -> {
            MenuChangeApplier.Workspace ws = loader.load(ctx.scope(), ctx.actorId());
            applier.apply(ws, ops);
            loader.persist(ws);
            return null;
        });
    }

    private static void authorise(AdminContext ctx, List<MenuOp> ops) {
        ctx.require(MenuAdminAccess.MANAGE);
        for (MenuOp op : ops) {
            if (op.op() != null && op.op().isAssignment()) ctx.require(MenuAdminAccess.ASSIGN);
        }
    }

    private Optional<MenuChangeSet> findOpen(MenuScope scope) {
        return scope.isGlobal()
                ? changeSets.findOpenGlobal(scope.application().getId())
                : changeSets.findOpenForTenant(scope.application().getId(), scope.tenantId());
    }

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }
}
