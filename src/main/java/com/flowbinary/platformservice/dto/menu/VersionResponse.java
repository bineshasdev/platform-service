package com.flowbinary.platformservice.dto.menu;

import com.flowbinary.platformservice.model.MenuSnapshot;

import java.time.Instant;
import java.util.UUID;

public record VersionResponse(int version, String note, UUID publishedBy, Instant publishedAt) {

    public static VersionResponse of(MenuSnapshot s) {
        return new VersionResponse(s.getVersion(), s.getNote(), s.getPublishedBy(), s.getPublishedAt());
    }
}
