package com.flowbinary.platformservice.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * One entry in the permission catalogue. Roles compose privileges; menu items
 * may require one to be visible. Maps to: privileges
 */
@Entity
@Table(name = "privileges")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Privilege extends BaseEntity {

    /** Stable dotted code, e.g. {@code patient.register}. */
    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    /** Grouping shown in the custom-role builder, e.g. {@code CLINICAL}. */
    @Column(name = "category", nullable = false)
    private String category;

    /** Owning product module, e.g. {@code M05}. Nullable for platform-wide privileges. */
    @Column(name = "module_code")
    private String moduleCode;

    /**
     * Acting under this privilege touches PHI, so the care-relationship and
     * consent layers must be evaluated on top of the RBAC check (FR-020).
     */
    @Column(name = "is_phi", nullable = false)
    @Builder.Default
    private Boolean isPhi = false;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}
