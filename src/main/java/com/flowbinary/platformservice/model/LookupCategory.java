package com.flowbinary.platformservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Groups related lookup values (e.g., COUNTRY, INDUSTRY, CURRENCY).
 * Maps to: lookup_categories
 */
@Entity
@Table(name = "lookup_categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LookupCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "is_system", nullable = false)
    @Builder.Default
    private Boolean isSystem = true;

    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<LookupValue> values = new ArrayList<>();
}
