-- ============================================================
-- V10 — Tenant types + credential / licence verification
--   * tenants.type / verification_status / listed / verified_at
--   * tenant_legal_profiles  — country-aware legal identity (1:1)
--   * tenant_credentials     — licences / registrations for review (1:N)
--   * lookup data for the signup UI (tenant_types)
-- ============================================================

-- ── Tenant: business type + verification state ───────────────────────────────
ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS type                VARCHAR(20),
    ADD COLUMN IF NOT EXISTS verification_status VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS listed              BOOLEAN      NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS verified_at         TIMESTAMPTZ;

-- Backfill rows created by the legacy signup flow (TenantService)
UPDATE tenants SET type = 'CLINIC' WHERE type IS NULL;

ALTER TABLE tenants
    ADD CONSTRAINT chk_tenants_type
        CHECK (type IS NULL OR type IN ('CLINIC','LAB','STORE','PRIVATE_CLINIC')),
    ADD CONSTRAINT chk_tenants_verification_status
        CHECK (verification_status IN ('PENDING','IN_REVIEW','VERIFIED','REJECTED'));

CREATE INDEX IF NOT EXISTS idx_tenants_type   ON tenants(type);
CREATE INDEX IF NOT EXISTS idx_tenants_listed ON tenants(listed) WHERE listed = true;

-- ── Tenant legal profile (1:1) ──────────────────────────────────────────────
CREATE TABLE tenant_legal_profiles (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID        NOT NULL UNIQUE REFERENCES tenants(id) ON DELETE CASCADE,
    legal_name          VARCHAR(255),
    entity_type         VARCHAR(50),
    registration_number VARCHAR(120),
    tax_id              VARCHAR(120),
    address_line1       VARCHAR(255),
    address_line2       VARCHAR(255),
    city                VARCHAR(120),
    state               VARCHAR(120),
    zip                 VARCHAR(20),
    country             VARCHAR(2),
    contact_email       VARCHAR(255),
    contact_phone       VARCHAR(30),
    data_protection     JSONB       NOT NULL DEFAULT '{}',
    attributes          JSONB       NOT NULL DEFAULT '{}',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_tenant_legal_profiles_tenant ON tenant_legal_profiles(tenant_id);

-- ── Tenant credentials / licences (1:N) ─────────────────────────────────────
CREATE TABLE tenant_credentials (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    type              VARCHAR(60) NOT NULL,
    country           VARCHAR(2),
    credential_number VARCHAR(120),
    issuing_authority VARCHAR(200),
    holder_name       VARCHAR(200),
    issue_date        DATE,
    expiry_date       DATE,
    status            VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED'
                          CHECK (status IN ('SUBMITTED','UNDER_REVIEW','VERIFIED','REJECTED','EXPIRED')),
    document_file_id  UUID        REFERENCES tenant_files(id) ON DELETE SET NULL,
    reviewed_by       UUID,
    reviewed_at       TIMESTAMPTZ,
    review_notes      TEXT,
    attributes        JSONB       NOT NULL DEFAULT '{}',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_tenant_credentials_tenant ON tenant_credentials(tenant_id);
CREATE INDEX idx_tenant_credentials_status ON tenant_credentials(tenant_id, status);
CREATE INDEX idx_tenant_credentials_expiry ON tenant_credentials(expiry_date);

-- ── Lookup data for the signup UI ──────────────────────────────────────────
INSERT INTO lookup_categories (code, name) VALUES
    ('tenant_types', 'Tenant Types')
ON CONFLICT (code) DO NOTHING;

INSERT INTO lookup_values (category_id, code, label, sort_order)
SELECT c.id, v.code, v.label, v.sort_order
FROM lookup_categories c,
(VALUES
    ('CLINIC',         'Clinic',              1),
    ('PRIVATE_CLINIC', 'Private Practice',    2),
    ('LAB',            'Diagnostic Lab',      3),
    ('STORE',          'Pharmacy / Store',    4)
) AS v(code, label, sort_order)
WHERE c.code = 'tenant_types'
ON CONFLICT (category_id, code) DO NOTHING;
