package com.flowbinary.platformservice.controller;

import com.flowbinary.platformservice.dto.lookup.LookupCategoryResponse;
import com.flowbinary.platformservice.dto.lookup.LookupValueResponse;
import com.flowbinary.platformservice.service.LookupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/lookups")
@RequiredArgsConstructor
@Tag(name = "Lookups", description = "Reference data / lookup endpoints")
public class LookupController {

    private final LookupService lookupService;

    /**
     * GET /api/v1/lookups
     * List all categories (code + name, no values).
     */
    @GetMapping
    @Operation(summary = "List all lookup categories")
    List<LookupCategoryResponse> listCategories() {
        return lookupService.listCategories();
    }

    /**
     * GET /api/v1/lookups/bulk?codes=COUNTRY,CURRENCY,INDUSTRY
     * Fetch values for several categories in one request.
     * Returns: { "COUNTRY": { code, name, values: [...] }, ... }
     */
    @GetMapping("/bulk")
    @Operation(summary = "Fetch multiple lookup categories at once")
    Map<String, LookupCategoryResponse> getBulk(@RequestParam List<String> codes) {
        return lookupService.getBulk(codes);
    }

    /**
     * GET /api/v1/lookups/{categoryCode}
     * All active values for a category, ordered by sortOrder.
     */
    @GetMapping("/{categoryCode}")
    @Operation(summary = "Get all values for a lookup category")
    LookupCategoryResponse getCategory(@PathVariable String categoryCode) {
        return lookupService.getCategory(categoryCode);
    }

    /**
     * GET /api/v1/lookups/{categoryCode}/children/{parentCode}
     * Child values for hierarchical lookups (e.g. states for a country code).
     */
    @GetMapping("/{categoryCode}/children/{parentCode}")
    @Operation(summary = "Get child values for a hierarchical lookup")
    List<LookupValueResponse> getChildren(
            @PathVariable String categoryCode,
            @PathVariable String parentCode) {
        return lookupService.getChildren(categoryCode, parentCode);
    }

    /**
     * GET /api/v1/lookups/{categoryCode}/{valueCode}
     * Single lookup value.
     */
    @GetMapping("/{categoryCode}/{valueCode}")
    @Operation(summary = "Get a single lookup value")
    LookupValueResponse getValue(
            @PathVariable String categoryCode,
            @PathVariable String valueCode) {
        return lookupService.getValue(categoryCode, valueCode);
    }
}
