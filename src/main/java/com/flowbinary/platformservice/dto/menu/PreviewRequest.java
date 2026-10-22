package com.flowbinary.platformservice.dto.menu;

import java.util.UUID;

/**
 * Exactly one of {@code userId} or {@code roleCode}. {@code useDraft} false
 * previews the currently published menu, to compare against the draft.
 */
public record PreviewRequest(UUID userId, String roleCode, Boolean useDraft, UUID tenantId) {

    public boolean draft() {
        return useDraft == null || useDraft;
    }
}
