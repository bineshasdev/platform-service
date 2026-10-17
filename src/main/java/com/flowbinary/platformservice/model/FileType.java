package com.flowbinary.platformservice.model;

import java.util.Set;

/**
 * Classifies uploaded files, enforcing per-type MIME and size constraints.
 */
public enum FileType {

    LOGO(
        5L * 1024 * 1024,   // 5 MB
        Set.of("image/png", "image/jpeg", "image/svg+xml", "image/webp")
    ),
    PROFILE_IMAGE(
        5L * 1024 * 1024,   // 5 MB
        Set.of("image/png", "image/jpeg", "image/webp", "image/gif")
    ),
    FAVICON(
        1L * 1024 * 1024,   // 1 MB
        Set.of("image/png", "image/x-icon", "image/vnd.microsoft.icon", "image/svg+xml")
    ),
    BANNER(
        10L * 1024 * 1024,  // 10 MB
        Set.of("image/png", "image/jpeg", "image/webp", "image/gif")
    ),
    DOCUMENT(
        50L * 1024 * 1024,  // 50 MB
        Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "text/plain",
            "text/csv"
        )
    ),
    ATTACHMENT(
        25L * 1024 * 1024,  // 25 MB
        Set.of()            // empty = any MIME accepted
    );

    /** Maximum allowed file size in bytes for this type. */
    private final long maxSizeBytes;

    /** Allowed MIME types. Empty set means no restriction. */
    private final Set<String> allowedMimeTypes;

    FileType(long maxSizeBytes, Set<String> allowedMimeTypes) {
        this.maxSizeBytes    = maxSizeBytes;
        this.allowedMimeTypes = allowedMimeTypes;
    }

    public long maxSizeBytes()          { return maxSizeBytes;    }
    public Set<String> allowedMimeTypes() { return allowedMimeTypes; }

    /** Returns true when the MIME type is acceptable for this file type. */
    public boolean accepts(String mimeType) {
        if (allowedMimeTypes.isEmpty()) return true;
        // Strip parameters like "; charset=utf-8"
        String base = mimeType == null ? "" : mimeType.split(";")[0].trim().toLowerCase();
        return allowedMimeTypes.contains(base);
    }
}
