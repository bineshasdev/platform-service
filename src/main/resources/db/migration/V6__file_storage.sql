-- ============================================================
-- V6 — File Storage
--   tenant_storage_configs  — per-tenant backend + credentials + quota
--   tenant_files            — file metadata (one row per stored file)
--   file_data               — inline blob storage (DATABASE backend only)
-- ============================================================

-- ── Tenant storage configuration ─────────────────────────────────────────────
CREATE TABLE tenant_storage_configs (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID        NOT NULL UNIQUE REFERENCES tenants(id) ON DELETE CASCADE,

    -- Which backend to use for this tenant (LOCAL | S3 | DATABASE)
    backend      TEXT        NOT NULL DEFAULT 'LOCAL'
                     CHECK (backend IN ('LOCAL','S3','DATABASE')),

    -- Backend-specific credentials stored as JSONB.
    -- S3 example: { "accessKeyId": "...", "secretAccessKey": "...", "bucket": "...", "region": "...", "prefix": "..." }
    -- LOCAL example: { "basePath": "/opt/flowcore/uploads/{tenantSlug}" }
    -- DATABASE: no extra credentials needed; data stored in file_data table
    -- NOTE: In production, encrypt sensitive values before persisting.
    credentials  JSONB       NOT NULL DEFAULT '{}',

    -- Quota in megabytes; 0 = use platform default
    quota_mb     BIGINT      NOT NULL DEFAULT 0,

    -- Running total of bytes used across all stored files
    used_bytes   BIGINT      NOT NULL DEFAULT 0,

    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_storage_configs_tenant ON tenant_storage_configs(tenant_id);

-- ── Tenant files (metadata) ───────────────────────────────────────────────────
CREATE TABLE tenant_files (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,

    -- Type of asset (LOGO | PROFILE_IMAGE | FAVICON | BANNER | DOCUMENT | ATTACHMENT)
    file_type        TEXT        NOT NULL
                         CHECK (file_type IN ('LOGO','PROFILE_IMAGE','FAVICON','BANNER','DOCUMENT','ATTACHMENT')),

    -- Original filename as uploaded by the client
    original_name    TEXT        NOT NULL,

    -- Content-Type / MIME type detected or declared on upload
    content_type     TEXT        NOT NULL,

    -- File size in bytes
    size_bytes       BIGINT      NOT NULL,

    -- Storage backend that holds the actual bytes
    backend          TEXT        NOT NULL
                         CHECK (backend IN ('LOCAL','S3','DATABASE')),

    -- Opaque storage key:
    --   LOCAL    → absolute path on disk
    --   S3       → S3 object key (e.g. "tenant-abc/logos/uuid.png")
    --   DATABASE → UUID of the row in file_data
    storage_key      TEXT        NOT NULL,

    -- Optional public or CDN URL (set after upload if applicable)
    public_url       TEXT,

    -- Who uploaded this file (Keycloak user UUID; nullable for system uploads)
    uploaded_by      UUID,

    -- Logical status: ACTIVE | DELETED
    status           TEXT        NOT NULL DEFAULT 'ACTIVE'
                         CHECK (status IN ('ACTIVE','DELETED')),

    -- Extra arbitrary metadata (e.g. image dimensions, tags)
    metadata         JSONB       NOT NULL DEFAULT '{}',

    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_tenant_files_tenant       ON tenant_files(tenant_id);
CREATE INDEX idx_tenant_files_type         ON tenant_files(tenant_id, file_type);
CREATE INDEX idx_tenant_files_status       ON tenant_files(tenant_id, status);
CREATE INDEX idx_tenant_files_uploaded_by  ON tenant_files(uploaded_by);

-- ── Inline blob storage (DATABASE backend only) ───────────────────────────────
CREATE TABLE file_data (
    id          UUID   PRIMARY KEY DEFAULT gen_random_uuid(),
    file_id     UUID   NOT NULL UNIQUE REFERENCES tenant_files(id) ON DELETE CASCADE,
    data        BYTEA  NOT NULL
);

-- ── Auto-update updated_at via trigger ───────────────────────────────────────
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_tenant_storage_configs_updated_at
    BEFORE UPDATE ON tenant_storage_configs
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_tenant_files_updated_at
    BEFORE UPDATE ON tenant_files
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
