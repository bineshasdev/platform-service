package com.flowbinary.platformservice.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.flowbinary.platformservice.dto.bootstrap.BootstrapResponse;
import com.flowbinary.platformservice.service.BootstrapService;
import com.flowbinary.platformservice.service.MenuResolutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bootstrap")
@RequiredArgsConstructor
@Tag(name = "Bootstrap", description = "UI initialisation payload")
public class BootstrapController {

    private final BootstrapService bootstrapService;

    @GetMapping
    @PreAuthorize("@authz.member(authentication)")
    @Operation(
        summary = "Load UI bootstrap data",
        description = """
            Returns all data the UI needs on startup in a single request:
            tenant info, current user, subscription, feature flags,
            the navigation menu resolved for this user and application,
            merged settings and reference lookups.
            Requires a valid JWT with an `organization` claim.
            """
    )
    public BootstrapResponse bootstrap(
            Authentication authentication,
            @Parameter(description = "Application whose menu tree to resolve")
            @RequestParam(name = "application", defaultValue = MenuResolutionService.DEFAULT_APPLICATION)
            String application) {
        return bootstrapService.load(authentication, application);
    }
}
