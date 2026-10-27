package com.flowbinary.platformservice.controller;

import com.flowbinary.platformservice.dto.menu.CancelRequest;
import com.flowbinary.platformservice.dto.menu.TemporaryMenuRequest;
import com.flowbinary.platformservice.dto.menu.TemporaryMenuResponse;
import com.flowbinary.platformservice.model.MenuLifecycle;
import com.flowbinary.platformservice.service.MenuAdminAccess;
import com.flowbinary.platformservice.service.MenuAdminAccess.AdminContext;
import com.flowbinary.platformservice.service.MenuAdminAccess.ScopeKind;
import com.flowbinary.platformservice.service.MenuResolutionService;
import com.flowbinary.platformservice.service.TemporaryMenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Time-bound menus. Live, not drafted: every call takes effect immediately and
 * is audited. Operators manage platform-wide ones ({@code scope=GLOBAL}); a
 * tenant administrator manages their own tenant's.
 */
@RestController
@RequestMapping("/api/v1/admin/temporary-menus")
@RequiredArgsConstructor
@Tag(name = "Temporary menus", description = "Time-bound menus for previews, announcements and campaigns (UTC)")
public class TemporaryMenuController {

    private static final String APP = MenuResolutionService.DEFAULT_APPLICATION;

    private final MenuAdminAccess access;
    private final TemporaryMenuService service;

    private AdminContext ctx(Authentication auth, String application, ScopeKind scope, UUID tenantId) {
        return access.resolve(auth, application, scope, tenantId);
    }

    @GetMapping
    @Operation(summary = "List temporary menus, optionally by status (SCHEDULED, ACTIVE, EXPIRED, CANCELLED)")
    public List<TemporaryMenuResponse> list(Authentication auth,
                                            @RequestParam(required = false) MenuLifecycle status,
                                            @RequestParam(defaultValue = APP) String application,
                                            @RequestParam(defaultValue = "TENANT") ScopeKind scope,
                                            @RequestParam(required = false) UUID tenantId) {
        return service.list(ctx(auth, application, scope, tenantId), status);
    }

    @GetMapping("/{code}")
    public TemporaryMenuResponse get(Authentication auth, @PathVariable String code,
                                     @RequestParam(defaultValue = APP) String application,
                                     @RequestParam(defaultValue = "TENANT") ScopeKind scope,
                                     @RequestParam(required = false) UUID tenantId) {
        return service.get(ctx(auth, application, scope, tenantId), code);
    }

    @PostMapping
    @Operation(summary = "Create a temporary menu",
               description = "Takes effect at startsAt (now if omitted). Times are UTC, the end is exclusive.")
    public ResponseEntity<TemporaryMenuResponse> create(Authentication auth,
                                                        @Valid @RequestBody TemporaryMenuRequest body,
                                                        @RequestParam(defaultValue = APP) String application,
                                                        @RequestParam(defaultValue = "TENANT") ScopeKind scope,
                                                        @RequestParam(required = false) UUID tenantId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(ctx(auth, application, scope, tenantId), body));
    }

    @PutMapping("/{code}")
    @Operation(summary = "Update a temporary menu, or revive an expired one with a new window")
    public TemporaryMenuResponse update(Authentication auth, @PathVariable String code,
                                        @Valid @RequestBody TemporaryMenuRequest body,
                                        @RequestParam(defaultValue = APP) String application,
                                        @RequestParam(defaultValue = "TENANT") ScopeKind scope,
                                        @RequestParam(required = false) UUID tenantId) {
        return service.update(ctx(auth, application, scope, tenantId), code, body);
    }

    @PostMapping("/{code}/cancel")
    @Operation(summary = "Cancel now. The row is kept, with status CANCELLED.")
    public TemporaryMenuResponse cancel(Authentication auth, @PathVariable String code,
                                        @Valid @RequestBody(required = false) CancelRequest body,
                                        @RequestParam(defaultValue = APP) String application,
                                        @RequestParam(defaultValue = "TENANT") ScopeKind scope,
                                        @RequestParam(required = false) UUID tenantId) {
        return service.cancel(ctx(auth, application, scope, tenantId), code, body == null ? null : body.reason());
    }
}
