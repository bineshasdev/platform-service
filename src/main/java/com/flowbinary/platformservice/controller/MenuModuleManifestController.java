package com.flowbinary.platformservice.controller;

import com.flowbinary.platformservice.config.AppProperties;
import com.flowbinary.platformservice.dto.module.ModuleResponse;
import com.flowbinary.platformservice.model.ManifestOutcome;
import com.flowbinary.platformservice.repository.MenuModuleRepository;
import com.flowbinary.platformservice.security.ModuleIdentity;
import com.flowbinary.platformservice.service.MenuModuleSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;

/**
 * The endpoints a module calls itself. Authenticated by the module's own Keycloak
 * client-credentials token; the token's client must be the one registered as that
 * module's owner ({@link ModuleIdentity}).
 */
@RestController
@RequestMapping("/api/v1/internal/menu-modules")
@RequiredArgsConstructor
@Tag(name = "Module menu manifests", description = "Called by modules with their service token")
public class MenuModuleManifestController {

    private final ModuleIdentity identity;
    private final MenuModuleSyncService sync;
    private final MenuModuleRepository modules;
    private final AppProperties properties;

    @PutMapping(value = "/{code}/manifest", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Publish this module's menu",
               description = """
                       Replaces the module's menu with the manifest if it is valid; otherwise changes nothing and \
                       answers 422 with every problem found. Identical content is a cheap no-op. \
                       The last accepted menu keeps serving users whatever happens here.""")
    public ResponseEntity<MenuModuleSyncService.SyncResult> push(Authentication auth, @PathVariable String code,
                                                                 @RequestBody String body) {
        String clientId = identity.requireOwner(auth, code);
        long max = properties.getMenu().getModules().getMaxManifestBytes();
        if (body.getBytes(StandardCharsets.UTF_8).length > max) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
        }
        MenuModuleSyncService.SyncResult result = sync.accept(code, body, "PUSH", clientId);
        return ResponseEntity.status(result.outcome() == ManifestOutcome.ACCEPTED ? HttpStatus.OK : HttpStatus.UNPROCESSABLE_ENTITY)
                .body(result);
    }

    @GetMapping("/{code}")
    @Operation(summary = "This module's registration and sync state")
    @Transactional(readOnly = true)
    public ModuleResponse status(Authentication auth, @PathVariable String code) {
        identity.requireOwner(auth, code);
        return ModuleResponse.of(modules.findByCode(code).orElseThrow(() -> new NoSuchElementException("Module not found")));
    }
}
