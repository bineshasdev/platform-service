package com.flowbinary.platformservice.authz.model;

import com.flowbinary.platformservice.model.BaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.Map;

/**
 * One delivery of a service's authorization manifest, accepted or rejected.
 * Maps to: authz_manifests
 */
@Entity
@Table(name = "authz_manifests")
@Getter
@Setter
@NoArgsConstructor
public class AuthzManifestRecord extends BaseEntity {

    @Column(name = "service", nullable = false)
    private String service;

    @Column(name = "version")
    private String version;

    @Column(name = "content_hash", nullable = false)
    private String contentHash;

    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column(name = "outcome", nullable = false)
    private String outcome;

    @Type(JsonBinaryType.class)
    @Column(name = "errors", columnDefinition = "jsonb")
    private java.util.List<String> errors;

    @Type(JsonBinaryType.class)
    @Column(name = "summary", columnDefinition = "jsonb")
    private Map<String, Object> summary;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt = Instant.now();
}
