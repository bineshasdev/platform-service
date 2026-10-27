package com.flowbinary.platformservice.model;

import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.Instant;

/** One manifest delivery — accepted, rejected or failed — kept for audit and diagnosis. Maps to: menu_module_manifests */
@Entity
@Table(name = "menu_module_manifests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MenuModuleManifest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "module_id", nullable = false)
    private MenuModule module;

    @Column(name = "received_at", nullable = false)
    @Builder.Default
    private Instant receivedAt = Instant.now();

    /** PUSH or PULL. */
    @Column(name = "received_via", nullable = false)
    private String receivedVia;

    @Column(name = "client_id")
    private String clientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false)
    private ManifestOutcome outcome;

    @Column(name = "version")
    private String version;

    @Column(name = "content_hash")
    private String contentHash;

    @Type(JsonBinaryType.class)
    @Column(name = "manifest", columnDefinition = "jsonb")
    private JsonNode manifest;

    @Type(JsonBinaryType.class)
    @Column(name = "errors", columnDefinition = "jsonb")
    private JsonNode errors;

    @Type(JsonBinaryType.class)
    @Column(name = "summary", columnDefinition = "jsonb")
    private JsonNode summary;
}
