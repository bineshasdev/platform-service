package com.flowbinary.platformservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * User-level override for a setting. Highest precedence in the cascade.
 * Maps to: user_settings
 */
@Entity
@Table(name = "user_settings",
        uniqueConstraints = @UniqueConstraint(name = "uq_user_setting_key",
                columnNames = {"user_id", "key"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * References Supabase auth.users. Not a JPA FK.
     */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "key", referencedColumnName = "key", nullable = false)
    private SettingDefinition definition;

    @Column(name = "value", nullable = false, columnDefinition = "TEXT")
    private String value;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
