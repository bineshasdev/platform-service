-- ============================================================
-- V9 — otp_verifications
-- Email + mobile OTP verification. tenant_id/user_id are nullable
-- because OTPs can be requested before a tenant/user record exists
-- (e.g. signup contact verification).
-- ============================================================

CREATE TABLE otp_verifications (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID         REFERENCES tenants(id) ON DELETE CASCADE,
    user_id             UUID         REFERENCES users(id) ON DELETE CASCADE,
    channel             TEXT         NOT NULL CHECK (channel IN ('EMAIL', 'SMS')),
    target              VARCHAR(255) NOT NULL,   -- email address or E.164 phone number
    purpose             VARCHAR(50)  NOT NULL DEFAULT 'VERIFY_CONTACT',
    otp_hash            VARCHAR(255) NOT NULL,   -- SHA-256 of the OTP code; never store plaintext
    verification_token  VARCHAR(255) NOT NULL UNIQUE,
    attempt_count       INTEGER      NOT NULL DEFAULT 0,
    max_attempts        INTEGER      NOT NULL DEFAULT 5,
    expires_at          TIMESTAMPTZ  NOT NULL,
    verified_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ,
    updated_at          TIMESTAMPTZ
);

CREATE INDEX idx_otp_verifications_token          ON otp_verifications(verification_token);
CREATE INDEX idx_otp_verifications_target_channel ON otp_verifications(target, channel, created_at);
CREATE INDEX idx_otp_verifications_expires_at     ON otp_verifications(expires_at);
CREATE INDEX idx_otp_verifications_tenant_id      ON otp_verifications(tenant_id);
