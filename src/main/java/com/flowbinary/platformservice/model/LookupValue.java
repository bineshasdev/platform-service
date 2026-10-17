package com.flowbinary.platformservice.model;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.util.Map;
import java.util.UUID;

/**
 * A single selectable value within a lookup category.
 * Maps to: lookup_values
 */
@Entity
@Table(name = "lookup_values",
        uniqueConstraints = @UniqueConstraint(name = "uq_lookup_category_code",
                columnNames = {"category_id", "code"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LookupValue {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private LookupCategory category;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "label", nullable = false)
    private String label;

    /**
     * For hierarchical lookups, e.g. country code for a state lookup.
     */
    @Column(name = "parent_code")
    private String parentCode;

    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 0;

    @Type(JsonBinaryType.class)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}
