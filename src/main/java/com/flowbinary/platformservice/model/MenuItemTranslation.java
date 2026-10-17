package com.flowbinary.platformservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Localised label for a menu item. {@code MenuItem.label} is the default
 * fallback when a locale has no row here.
 * Maps to: menu_item_translations (composite PK)
 */
@Entity
@Table(name = "menu_item_translations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MenuItemTranslation {

    @EmbeddedId
    private MenuItemTranslationId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("menuItemId")
    @JoinColumn(name = "menu_item_id")
    private MenuItem menuItem;

    @Column(name = "label", nullable = false)
    private String label;

    @Column(name = "description")
    private String description;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
