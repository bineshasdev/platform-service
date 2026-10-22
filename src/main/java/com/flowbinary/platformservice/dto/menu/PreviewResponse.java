package com.flowbinary.platformservice.dto.menu;

import com.flowbinary.platformservice.dto.bootstrap.MenuEntry;

import java.util.List;
import java.util.UUID;

public record PreviewResponse(boolean usedDraft, UUID tenantId, List<MenuEntry> menu) {
}
