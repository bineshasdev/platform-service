-- ============================================================
-- V11 — Facility domain: clinics / labs / stores
--   One row per tenant (1:1). Common facility columns are repeated
--   in each table (mirrors the FacilityEntity @MappedSuperclass).
-- ============================================================

-- ── Clinics ─────────────────────────────────────────────────────────────────
CREATE TABLE clinics (
    -- common (FacilityEntity)
    id                       UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                UUID        NOT NULL UNIQUE REFERENCES tenants(id) ON DELETE CASCADE,
    name                     VARCHAR(255) NOT NULL,
    legal_name               VARCHAR(255),
    status                   VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'
                                 CHECK (status IN ('ACTIVE','INACTIVE','TEMPORARILY_CLOSED','PERMANENTLY_CLOSED')),
    email                    VARCHAR(255),
    phone                    VARCHAR(30),
    alternate_phone          VARCHAR(30),
    website                  VARCHAR(255),
    address_line1            VARCHAR(255),
    address_line2            VARCHAR(255),
    city                     VARCHAR(120),
    state                    VARCHAR(120),
    zip                      VARCHAR(20),
    country                  VARCHAR(2),
    latitude                 NUMERIC(9,6),
    longitude                NUMERIC(9,6),
    timezone                 VARCHAR(40),
    description              TEXT,
    operating_hours          JSONB       NOT NULL DEFAULT '{}',
    amenities                JSONB       NOT NULL DEFAULT '{}',
    settings                 JSONB       NOT NULL DEFAULT '{}',
    metadata                 JSONB       NOT NULL DEFAULT '{}',
    -- clinic-specific
    category                 VARCHAR(30)
                                 CHECK (category IS NULL OR category IN
                                     ('GENERAL','SINGLE_SPECIALTY','MULTI_SPECIALTY','DENTAL','EYE_CARE','AYUSH')),
    specialties              JSONB       NOT NULL DEFAULT '[]',
    appointment_mode         VARCHAR(20)
                                 CHECK (appointment_mode IS NULL OR appointment_mode IN ('WALK_IN','APPOINTMENT','BOTH')),
    teleconsultation_enabled BOOLEAN     NOT NULL DEFAULT false,
    default_consultation_fee NUMERIC(10,2),
    currency                 VARCHAR(3),
    practitioner_count       INTEGER,
    bed_count                INTEGER,
    has_in_house_pharmacy    BOOLEAN     NOT NULL DEFAULT false,
    has_in_house_lab         BOOLEAN     NOT NULL DEFAULT false,
    emergency_services       BOOLEAN     NOT NULL DEFAULT false,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_clinics_status  ON clinics(status);
CREATE INDEX idx_clinics_city    ON clinics(country, state, city);

-- ── Labs ────────────────────────────────────────────────────────────────────
CREATE TABLE labs (
    -- common (FacilityEntity)
    id                        UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                 UUID        NOT NULL UNIQUE REFERENCES tenants(id) ON DELETE CASCADE,
    name                      VARCHAR(255) NOT NULL,
    legal_name                VARCHAR(255),
    status                    VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'
                                  CHECK (status IN ('ACTIVE','INACTIVE','TEMPORARILY_CLOSED','PERMANENTLY_CLOSED')),
    email                     VARCHAR(255),
    phone                     VARCHAR(30),
    alternate_phone           VARCHAR(30),
    website                   VARCHAR(255),
    address_line1             VARCHAR(255),
    address_line2             VARCHAR(255),
    city                      VARCHAR(120),
    state                     VARCHAR(120),
    zip                       VARCHAR(20),
    country                   VARCHAR(2),
    latitude                  NUMERIC(9,6),
    longitude                 NUMERIC(9,6),
    timezone                  VARCHAR(40),
    description               TEXT,
    operating_hours           JSONB       NOT NULL DEFAULT '{}',
    amenities                 JSONB       NOT NULL DEFAULT '{}',
    settings                  JSONB       NOT NULL DEFAULT '{}',
    metadata                  JSONB       NOT NULL DEFAULT '{}',
    -- lab-specific
    category                  VARCHAR(30)
                                  CHECK (category IS NULL OR category IN
                                      ('PATHOLOGY','RADIOLOGY','MOLECULAR_DIAGNOSTICS','GENETICS','MULTI_MODALITY')),
    nabl_accredited           BOOLEAN     NOT NULL DEFAULT false,
    nabl_certificate_number   VARCHAR(60),
    nabl_valid_till           DATE,
    offers_pathology          BOOLEAN     NOT NULL DEFAULT false,
    offers_imaging            BOOLEAN     NOT NULL DEFAULT false,
    home_sample_collection    BOOLEAN     NOT NULL DEFAULT false,
    home_collection_radius_km INTEGER,
    average_turnaround_hours  INTEGER,
    report_delivery_modes     JSONB       NOT NULL DEFAULT '[]',
    test_catalog_size         INTEGER,
    parent_lab_name           VARCHAR(255),
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_labs_status ON labs(status);
CREATE INDEX idx_labs_city   ON labs(country, state, city);

-- ── Stores ──────────────────────────────────────────────────────────────────
CREATE TABLE stores (
    -- common (FacilityEntity)
    id                             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                      UUID        NOT NULL UNIQUE REFERENCES tenants(id) ON DELETE CASCADE,
    name                           VARCHAR(255) NOT NULL,
    legal_name                     VARCHAR(255),
    status                         VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'
                                       CHECK (status IN ('ACTIVE','INACTIVE','TEMPORARILY_CLOSED','PERMANENTLY_CLOSED')),
    email                          VARCHAR(255),
    phone                          VARCHAR(30),
    alternate_phone                VARCHAR(30),
    website                        VARCHAR(255),
    address_line1                  VARCHAR(255),
    address_line2                  VARCHAR(255),
    city                           VARCHAR(120),
    state                          VARCHAR(120),
    zip                            VARCHAR(20),
    country                        VARCHAR(2),
    latitude                       NUMERIC(9,6),
    longitude                      NUMERIC(9,6),
    timezone                       VARCHAR(40),
    description                    TEXT,
    operating_hours                JSONB       NOT NULL DEFAULT '{}',
    amenities                      JSONB       NOT NULL DEFAULT '{}',
    settings                       JSONB       NOT NULL DEFAULT '{}',
    metadata                       JSONB       NOT NULL DEFAULT '{}',
    -- store-specific
    category                       VARCHAR(30)
                                       CHECK (category IS NULL OR category IN
                                           ('RETAIL_PHARMACY','WHOLESALE_PHARMACY','HOSPITAL_PHARMACY','OTC_STORE')),
    drug_license_number            VARCHAR(120),
    drug_license_valid_till        DATE,
    gstin                          VARCHAR(20),
    pharmacist_in_charge_name      VARCHAR(255),
    pharmacist_registration_number VARCHAR(60),
    stocks_controlled_substances   BOOLEAN     NOT NULL DEFAULT false,
    open_24x7                      BOOLEAN     NOT NULL DEFAULT false,
    online_ordering_enabled        BOOLEAN     NOT NULL DEFAULT false,
    delivery_available             BOOLEAN     NOT NULL DEFAULT false,
    delivery_radius_km             INTEGER,
    min_order_value                NUMERIC(10,2),
    accepts_insurance              BOOLEAN     NOT NULL DEFAULT false,
    created_at                     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_stores_status ON stores(status);
CREATE INDEX idx_stores_city   ON stores(country, state, city);

-- ── updated_at triggers (function defined in V6) ────────────────────────────
CREATE TRIGGER trg_clinics_updated_at BEFORE UPDATE ON clinics
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_labs_updated_at BEFORE UPDATE ON labs
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_stores_updated_at BEFORE UPDATE ON stores
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
