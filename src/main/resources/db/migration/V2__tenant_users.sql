-- ============================================================
-- V2 — users
-- Platform users scoped to a tenant. auth_user_id holds the
-- Keycloak user UUID (no JPA FK — auth lives in Keycloak).
-- ============================================================

CREATE TABLE users (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID         NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    auth_user_id         UUID         NOT NULL,   -- Keycloak user ID
    role                 TEXT         NOT NULL,
    email                VARCHAR(255) NOT NULL,
    first_name           VARCHAR(100),
    last_name            VARCHAR(100),
    mobile               VARCHAR(20),
    country              VARCHAR(3),
    state                VARCHAR(100),
    pincode              VARCHAR(20),
    status               VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    start_date_effective TIMESTAMPTZ  DEFAULT now(),
    end_date_effective   TIMESTAMPTZ,
    metadata             JSONB        DEFAULT '{}',
    created_at           TIMESTAMPTZ,
    updated_at           TIMESTAMPTZ,
    CONSTRAINT uq_tenant_auth_user UNIQUE (tenant_id, auth_user_id),
    CONSTRAINT uq_tenant_email     UNIQUE (tenant_id, email)
);

CREATE INDEX idx_users_tenant_id    ON users(tenant_id);
CREATE INDEX idx_users_auth_user_id ON users(auth_user_id);
CREATE INDEX idx_users_email        ON users(email);
CREATE INDEX idx_users_status       ON users(status);
