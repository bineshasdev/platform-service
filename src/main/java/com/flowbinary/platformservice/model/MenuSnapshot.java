package com.flowbinary.platformservice.model;

import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * An immutable published version of a menu scope. {@code state} is the
 * canonical atom map produced by {@code MenuScopeStateReader}; rollback diffs
 * the live state against it. Version 0 is the baseline taken before the first
 * draft of a scope.
 * Maps to: menu_snapshots
 */
@Entity
@Table(name = "menu_snapshots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MenuSnapshot extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Type(JsonBinaryType.class)
    @Column(name = "state", columnDefinition = "jsonb", nullable = false)
    private JsonNode state;

    /** The captured scope state as plain maps and lists. */
    public Map<String, Object> stateMap() {
        return MenuJson.toMap(state);
    }

    @Column(name = "change_set_id")
    private UUID changeSetId;

    @Column(name = "note")
    private String note;

    @Column(name = "published_by")
    private UUID publishedBy;

    @Column(name = "published_at", nullable = false)
    @Builder.Default
    private Instant publishedAt = Instant.now();
}
