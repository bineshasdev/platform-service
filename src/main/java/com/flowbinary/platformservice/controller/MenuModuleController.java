package com.flowbinary.platformservice.controller;

import com.flowbinary.platformservice.authz.sdk.RequiresPrivilege;
import com.flowbinary.platformservice.dto.module.ManifestHistoryResponse;
import com.flowbinary.platformservice.dto.module.ModuleRequest;
import com.flowbinary.platformservice.dto.module.ModuleResponse;
import com.flowbinary.platformservice.service.*;
import com.flowbinary.platformservice.service.MenuAdminAccess.AdminContext;
import com.flowbinary.platformservice.service.MenuAdminAccess.ScopeKind;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Platform operators register and manage the modules that contribute menu items. */
@RestController
@RequestMapping("/api/v1/admin/menu-modules")
@RequiredArgsConstructor
@Tag(name = "Menu modules", description = "Register, approve, suspend and refresh menu-contributing modules (operators only)")
@RequiresPrivilege("platform.template.manage")
public class MenuModuleController {

    private static final String APP = MenuResolutionService.DEFAULT_APPLICATION;

    private final MenuAdminAccess access;
    private final MenuModuleRegistryService registry;
    private final MenuModulePuller puller;

    private AdminContext operator(Authentication auth) {
        return access.resolve(auth, APP, ScopeKind.GLOBAL, null);   // 403 unless a platform operator
    }

    @GetMapping
    public List<ModuleResponse> list(Authentication auth) {
        return registry.list(operator(auth));
    }

    @GetMapping("/{code}")
    public ModuleResponse get(Authentication auth, @PathVariable String code) {
        return registry.get(operator(auth), code);
    }

    @PostMapping
    @Operation(summary = "Register a module (PENDING until approved)")
    public ResponseEntity<ModuleResponse> register(Authentication auth, @Valid @RequestBody ModuleRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(registry.register(operator(auth), body));
    }

    @PutMapping("/{code}")
    @Operation(summary = "Change name, manifest URL, route prefixes or privilege namespace; applies from the next manifest")
    public ModuleResponse update(Authentication auth, @PathVariable String code, @Valid @RequestBody ModuleRequest body) {
        return registry.update(operator(auth), code, body);
    }

    @PostMapping("/{code}/approve")
    public ModuleResponse approve(Authentication auth, @PathVariable String code) {
        return registry.approve(operator(auth), code);
    }

    @PostMapping("/{code}/suspend")
    @Operation(summary = "Hide the module's items and refuse its manifests; reversible")
    public ModuleResponse suspend(Authentication auth, @PathVariable String code) {
        return registry.suspend(operator(auth), code);
    }

    @PostMapping("/{code}/resume")
    public ModuleResponse resume(Authentication auth, @PathVariable String code) {
        return registry.resume(operator(auth), code);
    }

    @DeleteMapping("/{code}")
    @Operation(summary = "Deregister: remove the module's items. The code stays reserved.")
    public ModuleResponse deregister(Authentication auth, @PathVariable String code) {
        return registry.deregister(operator(auth), code);
    }

    @PostMapping("/{code}/refresh")
    @Operation(summary = "Pull the manifest from the registered URL now",
               description = "Validated exactly like a pushed manifest. A failure leaves the last accepted menu in place.")
    public MenuModuleSyncService.SyncResult refresh(Authentication auth, @PathVariable String code) {
        operator(auth);
        return puller.pull(code);
    }

    @GetMapping("/{code}/manifests")
    @Operation(summary = "Recent manifest deliveries, newest first, with the reasons for any rejection")
    public List<ManifestHistoryResponse> history(Authentication auth, @PathVariable String code,
                                                 @RequestParam(defaultValue = "20") int limit) {
        return registry.history(operator(auth), code, limit);
    }
}
