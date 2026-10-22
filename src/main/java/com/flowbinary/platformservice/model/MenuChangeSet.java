package com.flowbinary.platformservice.model;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The draft of one menu scope — an ordered list of operations that preview
 * replays and publish applies. {@code menu_items} always holds the published
 * state; nothing in a change-set is visible to users until it is published.
 * Maps to: menu_change_sets
 */
@Entity
@Table(name = "menu_change_sets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MenuChangeSet extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    /** Null = the global menu. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private MenuChangeSetStatus status = MenuChangeSetStatus.OPEN;

    @Column(name = "title")
    private String title;

    @Type(JsonBinaryType.class)
    @Column(name = "ops", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<MenuOp> ops = new ArrayList<>();

    @Column(name = "base_version", nullable = false)
    private Integer baseVersion;

    /** Optimistic lock: two admins editing the same draft cannot silently overwrite each other. */
    @Version
    @Column(name = "row_version", nullable = false)
    private Long rowVersion;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "published_by")
    private UUID publishedBy;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "snapshot_id")
    private UUID snapshotId;
}
