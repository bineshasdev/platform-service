package com.flowbinary.platformservice.model;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Per-tenant storage configuration: which backend to use, backend credentials,
 * quota allocation, and running usage counter.
 *
 * <p>Credentials are stored as JSONB. In production environments sensitive values
 * (e.g. S3 secret access key) must be encrypted before persisting.
 *
 * <p>S3 credentials shape:
 * <pre>
 * {
 *   "accessKeyId":     "AKIA...",
 *   "secretAccessKey": "...",
 *   "bucket":          "my-bucket",
 *   "region":          "us-east-1",
 *   "prefix":          "tenant-slug/"   // optional key prefix
 *   "endpoint":        "https://..."    // optional: for MinIO / S3-compatible
 * }
 * </pre>
 *
 * <p>LOCAL credentials shape:
 * <pre>
 * { "basePath": "/opt/flowcore/uploads/tenant-slug" }
 * </pre>
 */
@Entity
@Table(name = "tenant_storage_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantStorageConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, unique = true)
    private Tenant tenant;

    @Enumerated(EnumType.STRING)
    @Column(name = "backend", nullable = false)
    @Builder.Default
    private StorageBackend backend = StorageBackend.LOCAL;

    @Type(JsonBinaryType.class)
    @Column(name = "credentials", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> credentials = Map.of();

    /**
     * Quota in megabytes. {@code 0} means fall back to the platform default
     * defined in {@code app.storage.default-quota-mb}.
     */
    @Column(name = "quota_mb", nullable = false)
    @Builder.Default
    private long quotaMb = 0L;

    /** Running total of bytes stored by this tenant (updated on upload/delete). */
    @Column(name = "used_bytes", nullable = false)
    @Builder.Default
    private long usedBytes = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
