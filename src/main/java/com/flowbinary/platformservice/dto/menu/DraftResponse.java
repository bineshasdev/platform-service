package com.flowbinary.platformservice.dto.menu;

import com.flowbinary.platformservice.model.MenuChangeSet;
import com.flowbinary.platformservice.model.MenuOp;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DraftResponse(UUID id,
                            String status,
                            String title,
                            int baseVersion,
                            int currentVersion,
                            /** True when the scope was published since this draft began: rebase before publishing. */
                            boolean stale,
                            long rowVersion,
                            List<MenuOp> ops,
                            UUID createdBy,
                            UUID updatedBy,
                            Instant updatedAt) {

    public static DraftResponse of(MenuChangeSet c, int currentVersion) {
        return new DraftResponse(c.getId(), c.getStatus().name(), c.getTitle(), c.getBaseVersion(), currentVersion,
                c.getBaseVersion() != currentVersion, c.getRowVersion(), List.copyOf(c.getOps()),
                c.getCreatedBy(), c.getUpdatedBy(), c.getUpdatedAt());
    }
}
