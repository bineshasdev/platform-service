package com.flowbinary.platformservice.dto.storage;

import com.flowbinary.platformservice.model.FileType;
import com.flowbinary.platformservice.model.StorageBackend;
import com.flowbinary.platformservice.model.TenantFile;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class FileMetadataResponse {

    private final UUID          id;
    private final FileType      fileType;
    private final String        originalName;
    private final String        contentType;
    private final long          sizeBytes;
    private final StorageBackend backend;
    private final String        publicUrl;
    private final String        status;
    private final UUID          uploadedBy;
    private final Instant       createdAt;
    private final Instant       updatedAt;

    public static FileMetadataResponse from(TenantFile f) {
        return FileMetadataResponse.builder()
                .id(f.getId())
                .fileType(f.getFileType())
                .originalName(f.getOriginalName())
                .contentType(f.getContentType())
                .sizeBytes(f.getSizeBytes())
                .backend(f.getBackend())
                .publicUrl(f.getPublicUrl())
                .status(f.getStatus())
                .uploadedBy(f.getUploadedBy())
                .createdAt(f.getCreatedAt())
                .updatedAt(f.getUpdatedAt())
                .build();
    }
}
