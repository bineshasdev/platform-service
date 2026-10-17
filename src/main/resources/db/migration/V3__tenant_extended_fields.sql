-- ============================================================
-- V3 — Add extended contact / locale fields to tenants
-- ============================================================

ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS mobile              VARCHAR(20),
    ADD COLUMN IF NOT EXISTS website             VARCHAR(255),
    ADD COLUMN IF NOT EXISTS country             VARCHAR(3)   DEFAULT 'IN',
    ADD COLUMN IF NOT EXISTS state               VARCHAR(100),
    ADD COLUMN IF NOT EXISTS zip                 VARCHAR(20),
    ADD COLUMN IF NOT EXISTS locale              VARCHAR(10)  DEFAULT 'en-GB',
    ADD COLUMN IF NOT EXISTS currency            VARCHAR(3)   DEFAULT 'INR',
    ADD COLUMN IF NOT EXISTS admin_temp_password VARCHAR(255);
