-- ============================================================
-- V1 — FlowCore Platform Service — initial schema
-- All PKs are UUID; column names match JPA entity @Column mappings.
-- ============================================================

-- ── Tenants ──────────────────────────────────────────────────────────
CREATE TABLE tenants (
    id                   UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            VARCHAR(255) UNIQUE,                -- Keycloak org ID (populated on signup)
    name                 VARCHAR(255) NOT NULL,
    display_name         VARCHAR(255) NOT NULL,
    email                VARCHAR(255) NOT NULL,
    slug                 TEXT         UNIQUE NOT NULL,
    plan                 TEXT         NOT NULL DEFAULT 'free'
                             CHECK (plan   IN ('free','starter','pro','enterprise')),
    status               TEXT         NOT NULL DEFAULT 'active'
                             CHECK (status IN ('active','suspended','cancelled')),
    mobile               VARCHAR(20),
    website              VARCHAR(255),
    country              VARCHAR(3)   DEFAULT 'IN',
    state                VARCHAR(100),
    zip                  VARCHAR(20),
    locale               VARCHAR(10)  DEFAULT 'en-GB',
    currency             VARCHAR(3)   DEFAULT 'INR',
    admin_temp_password  VARCHAR(255),
    effective_start_date TIMESTAMPTZ  DEFAULT now(),
    effective_end_date   TIMESTAMPTZ,
    settings             JSONB        DEFAULT '{}',
    metadata             JSONB        DEFAULT '{}',
    created_at           TIMESTAMPTZ,
    updated_at           TIMESTAMPTZ
);

-- ── Plans ─────────────────────────────────────────────────────────────
CREATE TABLE plans (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name          TEXT         NOT NULL,
    code          TEXT         UNIQUE NOT NULL
                      CHECK (code IN ('free','starter','pro','enterprise')),
    billing_cycle TEXT         CHECK (billing_cycle IN ('monthly','yearly')),
    price         DECIMAL(10,2) NOT NULL DEFAULT 0,
    is_active     BOOLEAN      NOT NULL DEFAULT true,
    metadata      JSONB        DEFAULT '{}',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ── Features ──────────────────────────────────────────────────────────
CREATE TABLE features (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    code        TEXT        UNIQUE NOT NULL,
    name        TEXT        NOT NULL,
    type        TEXT        NOT NULL
                    CHECK (type     IN ('BOOLEAN','USAGE_LIMITED','UNLIMITED')),
    category    TEXT        NOT NULL
                    CHECK (category IN ('AI','STORAGE','INTEGRATION','BRANDING','CORE','WORKFLOW')),
    description TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ── Plan-Feature matrix ───────────────────────────────────────────────
CREATE TABLE plan_features (
    plan_id      UUID    NOT NULL REFERENCES plans(id)    ON DELETE CASCADE,
    feature_id   UUID    NOT NULL REFERENCES features(id) ON DELETE CASCADE,
    enabled      BOOLEAN NOT NULL DEFAULT true,
    usage_limit  BIGINT,
    reset_period TEXT    CHECK (reset_period IN ('MONTHLY','DAILY','NONE')),
    PRIMARY KEY (plan_id, feature_id)
);

-- ── Tenant subscriptions ──────────────────────────────────────────────
CREATE TABLE tenant_subscriptions (
    id                   UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    plan_id              UUID        NOT NULL REFERENCES plans(id),
    status               TEXT        NOT NULL DEFAULT 'ACTIVE'
                             CHECK (status IN ('ACTIVE','TRIAL','PAST_DUE','CANCELLED')),
    current_period_start TIMESTAMPTZ NOT NULL DEFAULT now(),
    current_period_end   TIMESTAMPTZ,
    payment_provider_id  TEXT,
    cancel_at_period_end BOOLEAN     NOT NULL DEFAULT false,
    created_at           TIMESTAMPTZ,
    updated_at           TIMESTAMPTZ
);

-- ── Usage tracking ────────────────────────────────────────────────────
CREATE TABLE usage_records (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID        NOT NULL REFERENCES tenants(id)    ON DELETE CASCADE,
    feature_id   UUID        NOT NULL REFERENCES features(id),
    usage_count  BIGINT      NOT NULL DEFAULT 0,
    period       TEXT        NOT NULL,
    last_updated TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, feature_id, period)
);

-- ── Navigation menus ─────────────────────────────────────────────────
CREATE TABLE menu_items (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id        UUID        REFERENCES menu_items(id) ON DELETE CASCADE,
    code             TEXT        UNIQUE NOT NULL,
    label            TEXT        NOT NULL,
    icon             TEXT,
    route            TEXT,
    type             TEXT        NOT NULL DEFAULT 'LINK'
                         CHECK (type IN ('LINK','GROUP','DIVIDER','EXTERNAL')),
    sort_order       INT         NOT NULL DEFAULT 0,
    is_active        BOOLEAN     NOT NULL DEFAULT true,
    required_feature TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE menu_role_access (
    menu_item_id UUID NOT NULL REFERENCES menu_items(id) ON DELETE CASCADE,
    role         TEXT NOT NULL
                     CHECK (role IN ('OWNER','ADMIN','MANAGER','USER','VIEWER')),
    PRIMARY KEY (menu_item_id, role)
);

CREATE TABLE menu_plan_access (
    menu_item_id UUID NOT NULL REFERENCES menu_items(id) ON DELETE CASCADE,
    plan         TEXT NOT NULL
                     CHECK (plan IN ('free','starter','pro','enterprise')),
    PRIMARY KEY (menu_item_id, plan)
);

-- ── Settings framework ────────────────────────────────────────────────
CREATE TABLE setting_definitions (
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    key                TEXT        UNIQUE NOT NULL,
    category           TEXT        NOT NULL
                           CHECK (category  IN ('APPEARANCE','LOCALE','AI','NOTIFICATIONS','SECURITY','GENERAL')),
    data_type          TEXT        NOT NULL
                           CHECK (data_type IN ('STRING','BOOLEAN','INTEGER','JSON','ENUM')),
    default_value      TEXT,
    allowed_values     JSONB,
    tenant_overridable BOOLEAN     NOT NULL DEFAULT true,
    user_overridable   BOOLEAN     NOT NULL DEFAULT false,
    scope              TEXT        NOT NULL DEFAULT 'ALL'
                           CHECK (scope IN ('APP','TENANT','USER','ALL')),
    description        TEXT,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE app_settings (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    key        TEXT        NOT NULL REFERENCES setting_definitions(key) ON DELETE CASCADE,
    value      TEXT        NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (key)
);

CREATE TABLE tenant_settings (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id  UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    key        TEXT        NOT NULL REFERENCES setting_definitions(key) ON DELETE CASCADE,
    value      TEXT        NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, key)
);

CREATE TABLE user_settings (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL,
    key        TEXT        NOT NULL REFERENCES setting_definitions(key) ON DELETE CASCADE,
    value      TEXT        NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, key)
);

-- ── Branding / themes ─────────────────────────────────────────────────
CREATE TABLE tenant_themes (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID        UNIQUE NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    app_name         TEXT,
    logo_url         TEXT,
    favicon_url      TEXT,
    login_banner_url TEXT,
    mode             TEXT        NOT NULL DEFAULT 'light'
                         CHECK (mode IN ('light','dark','system')),
    colors           JSONB       NOT NULL
                         DEFAULT '{"primary":"#3ecf8e","secondary":"#1e293b","accent":"#f59e0b","background":"#ffffff","surface":"#f8fafc","error":"#ef4444","success":"#22c55e"}',
    border_radius    TEXT        DEFAULT '8px',
    font_family      TEXT        DEFAULT 'Inter, sans-serif',
    custom_css       TEXT,
    created_at       TIMESTAMPTZ,
    updated_at       TIMESTAMPTZ
);

-- ── Lookup / reference data ───────────────────────────────────────────
CREATE TABLE lookup_categories (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    code       TEXT        UNIQUE NOT NULL,
    name       TEXT        NOT NULL,
    is_system  BOOLEAN     NOT NULL DEFAULT true,
    version    INT         NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE lookup_values (
    id          UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id UUID    NOT NULL REFERENCES lookup_categories(id) ON DELETE CASCADE,
    code        TEXT    NOT NULL,
    label       TEXT    NOT NULL,
    parent_code TEXT,
    sort_order  INT     DEFAULT 0,
    metadata    JSONB   DEFAULT '{}',
    is_active   BOOLEAN NOT NULL DEFAULT true,
    UNIQUE (category_id, code)
);

-- ── Indexes ───────────────────────────────────────────────────────────
CREATE INDEX idx_tenants_slug    ON tenants(slug);
CREATE INDEX idx_tenants_email   ON tenants(email);
CREATE INDEX idx_tenants_status  ON tenants(status);
CREATE INDEX idx_menu_items_sort ON menu_items(parent_id, sort_order);
