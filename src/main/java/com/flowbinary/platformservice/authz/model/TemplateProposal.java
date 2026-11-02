package com.flowbinary.platformservice.authz.model;

import com.flowbinary.platformservice.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A service's request that a role template hold one of its privileges. Nothing changes until an
 * operator approves it.
 * Maps to: authz_template_proposals
 */
@Entity
@Table(name = "authz_template_proposals")
@Getter
@Setter
@NoArgsConstructor
public class TemplateProposal extends BaseEntity {

    @Column(name = "service", nullable = false)
    private String service;

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Column(name = "privilege_code", nullable = false)
    private String privilegeCode;

    @Column(name = "status", nullable = false)
    private String status = "PENDING";

    @Column(name = "manifest_id")
    private UUID manifestId;

    @Column(name = "proposed_at", nullable = false)
    private Instant proposedAt = Instant.now();

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "reason")
    private String reason;
}
