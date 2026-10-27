package com.flowbinary.platformservice.model;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The platform-owned master menu catalogue. One tree per {@link Application};
 * {@code code} is unique within an application, not globally.
 *
 * <p>Visibility of an item for a given user is the conjunction of:
 * plan access ({@link MenuPlanAccess}), feature flag ({@link #requiredFeature}),
 * privilege ({@link #requiredPrivilege}), and a role or user grant
 * ({@link MenuRoleAccess} / {@link MenuUserAccess}) with no user-level DENY.
 * Tenants may then relabel, reorder or hide an item via {@link MenuTenantOverride}.
 *
 * Maps to: menu_items
 */
@Entity
@Table(name = "menu_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MenuItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    /** Null = platform-global item; set = owned by (and only visible to) that tenant. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private MenuItem parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<MenuItem> children = new ArrayList<>();

    @Column(name = "code", nullable = false)
    private String code;

    /** Default (English) label. Localised variants live in {@link MenuItemTranslation}. */
    @Column(name = "label", nullable = false)
    private String label;

    @Column(name = "description")
    private String description;

    @Column(name = "icon")
    private String icon;

    @Column(name = "route")
    private String route;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    @Builder.Default
    private MenuItemType type = MenuItemType.LINK;

    @Enumerated(EnumType.STRING)
    @Column(name = "target", nullable = false)
    @Builder.Default
    private MenuTarget target = MenuTarget.SELF;

    /** Set for {@link MenuSource#MODULE} items: the service that owns (and syncs) them. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "module_id")
    private MenuModule module;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    @Builder.Default
    private MenuSource source = MenuSource.PLATFORM;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false)
    @Builder.Default
    private MenuLifecycle lifecycleStatus = MenuLifecycle.ACTIVE;

    /** Start of the display window, UTC, inclusive. Set for temporary menus. */
    @Column(name = "starts_at")
    private Instant startsAt;

    /** End of the display window, UTC, exclusive. Set for temporary menus. */
    @Column(name = "ends_at")
    private Instant endsAt;

    /** Short marker such as "New". */
    @Column(name = "badge")
    private String badge;

    /** Orders temporary menus among siblings with equal sort order; higher first. */
    @Column(name = "priority", nullable = false)
    @Builder.Default
    private Integer priority = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "audience_mode", nullable = false)
    @Builder.Default
    private MenuAudienceMode audienceMode = MenuAudienceMode.RESTRICTED;

    public boolean isTemporary() {
        return source == MenuSource.TEMPORARY;
    }

    public boolean isModule() {
        return source == MenuSource.MODULE;
    }

    /** True when the item may be shown at {@code at}: a live status and, for temporary menus, inside the window. */
    public boolean isLiveAt(Instant at) {
        if (!isTemporary()) return lifecycleStatus == MenuLifecycle.ACTIVE;
        return lifecycleStatus.isLiveCandidate()
                && startsAt != null && endsAt != null
                && !startsAt.isAfter(at) && endsAt.isAfter(at);
    }

    /** Whether activating the item navigates, expands its children, or both. */
    @Enumerated(EnumType.STRING)
    @Column(name = "behavior", nullable = false)
    @Builder.Default
    private MenuBehavior behavior = MenuBehavior.NAVIGATE;

    /** Where the item renders in its application. */
    @Enumerated(EnumType.STRING)
    @Column(name = "placement", nullable = false)
    @Builder.Default
    private MenuPlacement placement = MenuPlacement.LEFT;

    /**
     * {@link TenantType} names the item applies to; null = every type. A mandatory
     * gate like plan and feature. Only global items carry it.
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "tenant_types", columnDefinition = "text[]")
    private List<String> tenantTypes;

    /** Render a divider above this item. */
    @Column(name = "is_separator_before", nullable = false)
    @Builder.Default
    private Boolean separatorBefore = false;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    /** Depth from the root, 0-based. Maintained alongside {@link #path}. */
    @Column(name = "depth", nullable = false)
    @Builder.Default
    private Integer depth = 0;

    /** Materialised path of codes, e.g. {@code admin/admin_users}. */
    @Column(name = "path")
    private String path;

    /** False disables the item outright — it resolves for nobody. */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /** False keeps the route reachable but hides it from navigation. */
    @Column(name = "is_visible", nullable = false)
    @Builder.Default
    private Boolean isVisible = true;

    /** Feature code that must be enabled for this item to appear. NULL = always. */
    @Column(name = "required_feature")
    private String requiredFeature;

    /** Privilege the user must hold for this item to appear. NULL = no privilege gate. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "required_privilege_id")
    private Privilege requiredPrivilege;

    @Type(JsonBinaryType.class)
    @Column(name = "metadata", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    /** Soft delete. Non-null rows are excluded from every resolution query. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @BatchSize(size = 200)
    @OneToMany(mappedBy = "menuItem", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<MenuRoleAccess> roleAccess = new ArrayList<>();

    @BatchSize(size = 200)
    @OneToMany(mappedBy = "menuItem", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<MenuPlanAccess> planAccess = new ArrayList<>();

    @BatchSize(size = 200)
    @OneToMany(mappedBy = "menuItem", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<MenuItemTranslation> translations = new ArrayList<>();

    /** Label for a locale, falling back to the default {@link #label}. */
    public String labelFor(String locale) {
        if (locale == null) return label;
        return translations.stream()
                .filter(t -> locale.equalsIgnoreCase(t.getId().getLocale()))
                .map(MenuItemTranslation::getLabel)
                .findFirst()
                .orElse(label);
    }
}
