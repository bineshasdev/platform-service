package com.flowbinary.platformservice.model;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.util.Map;

/**
 * White-label branding configuration per tenant.
 * Maps to: tenant_themes (one per tenant)
 */
@Entity
@Table(name = "tenant_themes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantTheme extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, unique = true)
    private Tenant tenant;

    @Column(name = "app_name")
    private String appName;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "favicon_url")
    private String faviconUrl;

    @Column(name = "login_banner_url")
    private String loginBannerUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false)
    @Builder.Default
    private ThemeMode mode = ThemeMode.light;

    /**
     * Color palette as JSONB: primary, secondary, accent, background, surface, error, success.
     */
    @Type(JsonBinaryType.class)
    @Column(name = "colors", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> colors;

    @Column(name = "border_radius")
    @Builder.Default
    private String borderRadius = "8px";

    @Column(name = "font_family")
    @Builder.Default
    private String fontFamily = "Inter, sans-serif";

    @Column(name = "custom_css", columnDefinition = "TEXT")
    private String customCss;
}
