-- ============================================================
-- V13 — Doctor <-> Clinic affiliations (many-to-many)
-- ============================================================

CREATE TABLE doctor_clinic_affiliations (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    doctor_id        UUID        NOT NULL REFERENCES doctors(id) ON DELETE CASCADE,
    clinic_id        UUID        NOT NULL REFERENCES clinics(id) ON DELETE CASCADE,
    affiliation_type VARCHAR(20)
                         CHECK (affiliation_type IS NULL OR affiliation_type IN
                             ('FULL_TIME','VISITING','CONSULTANT','LOCUM')),
    designation      VARCHAR(255),
    department       VARCHAR(120),
    consultation_fee NUMERIC(10,2),
    currency         VARCHAR(3),
    primary_clinic   BOOLEAN     NOT NULL DEFAULT false,
    status           VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    start_date       DATE,
    end_date         DATE,
    schedule         JSONB       NOT NULL DEFAULT '{}',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_doctor_clinic UNIQUE (doctor_id, clinic_id)
);

CREATE INDEX idx_dca_doctor  ON doctor_clinic_affiliations(doctor_id);
CREATE INDEX idx_dca_clinic  ON doctor_clinic_affiliations(clinic_id);
-- at most one primary clinic per doctor
CREATE UNIQUE INDEX uq_dca_primary_per_doctor
    ON doctor_clinic_affiliations(doctor_id) WHERE primary_clinic = true;

CREATE TRIGGER trg_dca_updated_at BEFORE UPDATE ON doctor_clinic_affiliations
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
