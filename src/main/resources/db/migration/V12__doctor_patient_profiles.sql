-- ============================================================
-- V12 — Platform-wide people: doctors & patients
--   Not tenant-scoped. Auth lives in Keycloak (auth_user_id).
-- ============================================================

-- ── Doctors ─────────────────────────────────────────────────────────────────
CREATE TABLE doctors (
    id                    UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    auth_user_id          UUID        NOT NULL UNIQUE,
    first_name            VARCHAR(80) NOT NULL,
    last_name             VARCHAR(80) NOT NULL,
    gender                VARCHAR(20)
                              CHECK (gender IS NULL OR gender IN ('MALE','FEMALE','OTHER','UNDISCLOSED')),
    date_of_birth         DATE,
    email                 VARCHAR(255) NOT NULL UNIQUE,
    phone                 VARCHAR(20)  UNIQUE,
    profile_image_url     TEXT,
    headline              VARCHAR(255),
    bio                   TEXT,
    primary_specialty     VARCHAR(120),
    specialties           JSONB       NOT NULL DEFAULT '[]',
    qualifications        JSONB       NOT NULL DEFAULT '[]',
    years_of_experience   INTEGER,
    languages             JSONB       NOT NULL DEFAULT '[]',
    registration_number   VARCHAR(60),
    registration_council  VARCHAR(160),
    registration_year     INTEGER,
    registration_state    VARCHAR(120),
    verification_status   VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                              CHECK (verification_status IN ('PENDING','IN_REVIEW','VERIFIED','REJECTED')),
    verified_at           TIMESTAMPTZ,
    listed                BOOLEAN     NOT NULL DEFAULT false,
    city                  VARCHAR(120),
    state                 VARCHAR(120),
    country               VARCHAR(2),
    owned_tenant_id       UUID        REFERENCES tenants(id) ON DELETE SET NULL,
    status                VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    email_verified        BOOLEAN     NOT NULL DEFAULT false,
    phone_verified        BOOLEAN     NOT NULL DEFAULT false,
    metadata              JSONB       NOT NULL DEFAULT '{}',
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_doctors_verification_status ON doctors(verification_status);
CREATE INDEX idx_doctors_listed              ON doctors(listed) WHERE listed = true;
CREATE INDEX idx_doctors_primary_specialty   ON doctors(primary_specialty);
CREATE INDEX idx_doctors_location            ON doctors(country, state, city);
CREATE INDEX idx_doctors_owned_tenant        ON doctors(owned_tenant_id);

-- ── Patients ────────────────────────────────────────────────────────────────
CREATE TABLE patients (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    auth_user_id        UUID        NOT NULL UNIQUE,
    first_name          VARCHAR(80) NOT NULL,
    last_name           VARCHAR(80) NOT NULL,
    gender              VARCHAR(20)
                            CHECK (gender IS NULL OR gender IN ('MALE','FEMALE','OTHER','UNDISCLOSED')),
    date_of_birth       DATE,
    email               VARCHAR(255) NOT NULL UNIQUE,
    phone               VARCHAR(20)  UNIQUE,
    profile_image_url   TEXT,
    blood_group         VARCHAR(5),
    address_line1       VARCHAR(255),
    address_line2       VARCHAR(255),
    city                VARCHAR(120),
    state               VARCHAR(120),
    zip                 VARCHAR(20),
    country             VARCHAR(2),
    emergency_contact   JSONB       NOT NULL DEFAULT '{}',
    preferred_language  VARCHAR(40),
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    email_verified      BOOLEAN     NOT NULL DEFAULT false,
    phone_verified      BOOLEAN     NOT NULL DEFAULT false,
    marketing_opt_in    BOOLEAN     NOT NULL DEFAULT false,
    metadata            JSONB       NOT NULL DEFAULT '{}',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_patients_phone ON patients(phone);

-- ── updated_at triggers (function defined in V6) ────────────────────────────
CREATE TRIGGER trg_doctors_updated_at BEFORE UPDATE ON doctors
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_patients_updated_at BEFORE UPDATE ON patients
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
