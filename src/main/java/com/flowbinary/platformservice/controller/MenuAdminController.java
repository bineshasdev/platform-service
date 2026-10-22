package com.flowbinary.platformservice.controller;

import com.flowbinary.platformservice.dto.menu.*;
import com.flowbinary.platformservice.model.AuditLog;
import com.flowbinary.platformservice.service.*;
import com.flowbinary.platformservice.service.MenuAdminAccess.AdminContext;
import com.flowbinary.platformservice.service.MenuAdminAccess.ScopeKind;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Menu administration. Every endpoint resolves a <em>scope</em> first: the
 * caller's own tenant by default, or — for platform operators only — the global
 * menu ({@code scope=GLOBAL}) or another tenant ({@code tenantId}). A tenant
 * administrator's edits never reach the global menu or another tenant.
 */
@RestController
@RequestMapping("/api/v1/admin/menus")
@RequiredArgsConstructor
@Tag(name = "Menu administration", description = "Draft, preview, publish and roll back navigation menus")
public class MenuAdminController {

    private static final String APP = MenuResolutionService.DEFAULT_APPLICATION;

    private final MenuAdminAccess       access;
    private final MenuCatalogueService  catalogueService;
    private final MenuDraftService      draftService;
    private final MenuPreviewService    previewService;
    private final MenuPublishService    publishService;
    private final AuditService          auditService;

    // ── Catalogue ──────────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "Published catalogue of a scope, including disabled items")
    public List<CatalogueItem> catalogue(Authentication auth,
                                         @RequestParam(defaultValue = APP) String application,
                                         @RequestParam(defaultValue = "TENANT") ScopeKind scope,
                                         @RequestParam(required = false) UUID tenantId) {
        return catalogueService.catalogue(access.resolve(auth, application, scope, tenantId));
    }

    // ── Draft ──────────────────────────────────────────────────────────────

    @GetMapping("/draft")
    @Operation(summary = "The open draft of a scope (204 when none)")
    public ResponseEntity<DraftResponse> draft(Authentication auth,
                                               @RequestParam(defaultValue = APP) String application,
                                               @RequestParam(defaultValue = "TENANT") ScopeKind scope,
                                               @RequestParam(required = false) UUID tenantId) {
        return draftService.current(access.resolve(auth, application, scope, tenantId))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/draft/ops")
    @Operation(summary = "Append operations to the draft, creating it if needed",
               description = "Each operation is dry-run against the live menu before it is stored. "
                       + "Send expectedRowVersion when a draft already exists.")
    public DraftResponse append(Authentication auth, @Valid @RequestBody DraftOpsRequest body,
                                @RequestParam(defaultValue = APP) String application,
                                @RequestParam(defaultValue = "TENANT") ScopeKind scope,
                                @RequestParam(required = false) UUID tenantId) {
        return draftService.append(access.resolve(auth, application, scope, tenantId), body);
    }

    @PutMapping("/draft")
    @Operation(summary = "Replace the draft's whole operation list")
    public DraftResponse replace(Authentication auth, @Valid @RequestBody DraftOpsRequest body,
                                 @RequestParam(defaultValue = APP) String application,
                                 @RequestParam(defaultValue = "TENANT") ScopeKind scope,
                                 @RequestParam(required = false) UUID tenantId) {
        return draftService.replace(access.resolve(auth, application, scope, tenantId), body);
    }

    @DeleteMapping("/draft")
    @Operation(summary = "Discard the open draft")
    public ResponseEntity<Void> discard(Authentication auth,
                                        @RequestParam(defaultValue = APP) String application,
                                        @RequestParam(defaultValue = "TENANT") ScopeKind scope,
                                        @RequestParam(required = false) UUID tenantId) {
        draftService.discard(access.resolve(auth, application, scope, tenantId));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/draft/rebase")
    @Operation(summary = "Move a stale draft onto the current published version")
    public DraftResponse rebase(Authentication auth,
                                @RequestParam(defaultValue = APP) String application,
                                @RequestParam(defaultValue = "TENANT") ScopeKind scope,
                                @RequestParam(required = false) UUID tenantId) {
        return draftService.rebase(access.resolve(auth, application, scope, tenantId));
    }

    // ── Preview / publish / rollback ───────────────────────────────────────

    @PostMapping("/draft/preview")
    @Operation(summary = "Preview the menu for a user or a role, with or without the draft",
               description = "Runs the draft on the real rows inside a transaction that is always rolled back.")
    public PreviewResponse preview(Authentication auth, @RequestBody PreviewRequest body,
                                   @RequestParam(defaultValue = APP) String application,
                                   @RequestParam(defaultValue = "TENANT") ScopeKind scope,
                                   @RequestParam(required = false) UUID tenantId) {
        return previewService.preview(access.resolve(auth, application, scope, tenantId), body);
    }

    @PostMapping("/draft/publish")
    @Operation(summary = "Publish the open draft as the next version")
    public PublishResponse publish(Authentication auth, @Valid @RequestBody(required = false) PublishRequest body,
                                   @RequestParam(defaultValue = APP) String application,
                                   @RequestParam(defaultValue = "TENANT") ScopeKind scope,
                                   @RequestParam(required = false) UUID tenantId) {
        return publishService.publish(access.resolve(auth, application, scope, tenantId),
                body == null ? null : body.note());
    }

    @GetMapping("/versions")
    @Operation(summary = "Published versions of a scope, newest first")
    public List<VersionResponse> versions(Authentication auth,
                                          @RequestParam(defaultValue = APP) String application,
                                          @RequestParam(defaultValue = "TENANT") ScopeKind scope,
                                          @RequestParam(required = false) UUID tenantId) {
        return publishService.versions(access.resolve(auth, application, scope, tenantId));
    }

    @PostMapping("/rollback")
    @Operation(summary = "Open a draft that restores an earlier version",
               description = "Nothing changes until that draft is previewed and published.")
    public DraftResponse rollback(Authentication auth, @Valid @RequestBody RollbackRequest body,
                                  @RequestParam(defaultValue = APP) String application,
                                  @RequestParam(defaultValue = "TENANT") ScopeKind scope,
                                  @RequestParam(required = false) UUID tenantId) {
        return publishService.rollback(access.resolve(auth, application, scope, tenantId), body.toVersion());
    }

    // ── Audit ──────────────────────────────────────────────────────────────

    @GetMapping("/audit")
    @Operation(summary = "Audit trail for the caller's tenant (operators may see all)")
    public Page<AuditLog> audit(Authentication auth,
                                @RequestParam(defaultValue = "MENU") String entityType,
                                @RequestParam(required = false) String entityId,
                                @RequestParam(required = false) String action,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "50") int size,
                                @Parameter(description = "Operators only: restrict to one tenant")
                                @RequestParam(required = false) UUID tenantId) {
        AdminContext ctx = access.resolve(auth, APP, ScopeKind.TENANT, tenantId);
        ctx.require(MenuAdminAccess.AUDIT);
        UUID restrictTo = ctx.isOperator() && tenantId == null ? null : ctx.scope().tenantId();
        Instant end = to != null ? to : Instant.now().plusSeconds(1);
        Instant start = from != null ? from : end.minus(Duration.ofDays(30));
        return auditService.search(restrictTo, entityType, entityId, action, start, end, page, size);
    }
}
