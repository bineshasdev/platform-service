package com.flowbinary.platformservice.model;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * File metadata record.  One row per stored file regardless of backend.
 * Actual bytes live on the filesystem, in S3, or in the {@code file_data} table.
 */
@Entity
@Table(name = "tenant_files")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantFile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false)
    private FileType fileType;

    @Column(name = "original_name", nullable = false)
    private String originalName;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "backend", nullable = false)
    private StorageBackend backend;

    /**
     * Opaque key understood by the {@link com.flowbinary.platformservice.storage.StorageProvider}:
     * <ul>
     *   <li>LOCAL    — absolute file path</li>
     *   <li>S3       — S3 object key</li>
     *   <li>DATABASE — UUID of the {@code file_data} row</li>
     * </ul>
     */
    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    /** Public or CDN URL, populated after upload when applicable. */
    @Column(name = "public_url")
    private String publicUrl;

    /** Keycloak user UUID of the uploader; null for system/automated uploads. */
    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "status", nullable = false)
    @Builder.Default
    private String status = "ACTIVE";

    @Type(JsonBinaryType.class)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> metadata = Map.of();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
