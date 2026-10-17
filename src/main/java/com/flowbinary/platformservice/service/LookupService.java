package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.dto.lookup.LookupCategoryResponse;
import com.flowbinary.platformservice.dto.lookup.LookupValueResponse;
import com.flowbinary.platformservice.model.LookupCategory;
import com.flowbinary.platformservice.model.LookupValue;
import com.flowbinary.platformservice.repository.LookupCategoryRepository;
import com.flowbinary.platformservice.repository.LookupValueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LookupService {

    private final LookupCategoryRepository categoryRepository;
    private final LookupValueRepository    valueRepository;

    /** All categories (code + name only, no values). */
    public List<LookupCategoryResponse> listCategories() {
        return categoryRepository.findAll().stream()
                .map(c -> LookupCategoryResponse.builder()
                        .code(c.getCode())
                        .name(c.getName())
                        .build())
                .toList();
    }

    /** All active values for a single category, ordered by sortOrder. */
    public LookupCategoryResponse getCategory(String categoryCode) {
        LookupCategory category = categoryRepository.findByCode(categoryCode)
                .orElseThrow(() -> new NoSuchElementException("Lookup category not found: " + categoryCode));

        List<LookupValueResponse> values =
                valueRepository.findByCategoryCodeAndIsActiveTrueOrderBySortOrderAsc(categoryCode)
                        .stream()
                        .map(this::toValueResponse)
                        .toList();

        return LookupCategoryResponse.builder()
                .code(category.getCode())
                .name(category.getName())
                .values(values)
                .build();
    }

    /**
     * Fetch several categories in one call — returns a map of {@code code → response}.
     * Unknown codes are silently skipped.
     */
    public Map<String, LookupCategoryResponse> getBulk(List<String> codes) {
        return codes.stream()
                .filter(categoryRepository::existsByCode)
                .collect(Collectors.toMap(code -> code, this::getCategory));
    }

    /** Single lookup value by category + value code. */
    public LookupValueResponse getValue(String categoryCode, String valueCode) {
        return valueRepository.findByCategoryCodeAndCode(categoryCode, valueCode)
                .map(this::toValueResponse)
                .orElseThrow(() -> new NoSuchElementException(
                        "Lookup value not found: " + categoryCode + "/" + valueCode));
    }

    /**
     * Child values for hierarchical lookups (e.g. states within a country).
     * {@code parentCode} is the code of the parent value (e.g. "US").
     */
    public List<LookupValueResponse> getChildren(String categoryCode, String parentCode) {
        return valueRepository
                .findByCategoryCodeAndParentCodeOrderBySortOrderAsc(categoryCode, parentCode)
                .stream()
                .map(this::toValueResponse)
                .toList();
    }

    // ── Mapping ────────────────────────────────────────────────────────────

    private LookupValueResponse toValueResponse(LookupValue v) {
        return LookupValueResponse.builder()
                .code(v.getCode())
                .label(v.getLabel())
                .parentCode(v.getParentCode())
                .sortOrder(v.getSortOrder())
                .metadata(v.getMetadata())
                .build();
    }
}
