package com.flowbinary.platformservice.controller;

import com.flowbinary.platformservice.dto.bootstrap.MenuEntry;
import com.flowbinary.platformservice.model.MenuPlacement;
import com.flowbinary.platformservice.service.MenuQueryService;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/menus")
@RequiredArgsConstructor
@Tag(name = "Menus", description = "Navigation menu for the authenticated user")
public class MenuController {

    private final MenuQueryService menuQueryService;

    @GetMapping("/me")
    @Operation(
        summary = "Get my navigation menu",
        description = """
            Returns the menu tree the caller may see in the given application.
            Visibility is a navigation aid only — every protected API enforces
            its own permissions. Same data as `menus` in `/bootstrap`.
            With `placement`, only the subtrees that render there (for example
            `SETTINGS` for the settings hub's sub-navigation).
            """
    )
    public List<MenuEntry> myMenu(
            Authentication authentication,
            @Parameter(description = "Application whose menu tree to resolve")
            @RequestParam(name = "application", defaultValue = MenuResolutionService.DEFAULT_APPLICATION)
            String application,
            @Parameter(description = "Return only the entries that render in this placement")
            @RequestParam(name = "placement", required = false)
            MenuPlacement placement) {
        return menuQueryService.menuFor(authentication, application, placement);
    }
}
