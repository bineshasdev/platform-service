package com.flowbinary.platformservice.dto.storage;

import com.flowbinary.platformservice.model.FileType;
import com.flowbinary.platformservice.model.StorageBackend;
import com.flowbinary.platformservice.model.TenantFile;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class FileUploadResponse {

    private final UUID          id;
    private final FileType      fileType;
    private final String        originalName;
    private final String        contentType;
    private final long          sizeBytes;
    private final StorageBackend backend;
    /** Non-null when the file is publicly accessible (S3 public bucket or CDN). */
    private final String        publicUrl;
    /** Non-null when a time-limited download URL was generated. */
    private final String        downloadUrl;

    public static FileUploadResponse from(TenantFile f, String downloadUrl) {
        return FileUploadResponse.builder()
                .id(f.getId())
                .fileType(f.getFileType())
                .originalName(f.getOriginalName())
                .contentType(f.getContentType())
                .sizeBytes(f.getSizeBytes())
                .backend(f.getBackend())
                .publicUrl(f.getPublicUrl())
                .downloadUrl(downloadUrl)
                .build();
    }
}
