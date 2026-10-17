-- ============================================================
-- V17 — tenant admin-email verification
-- email_verified_at is set once the signup email is confirmed via OTP.
-- Tenants that existed before this migration were never asked to verify
-- (signup marked them verified in Keycloak), so they are grandfathered
-- in using their creation time rather than locked out of review submission.
-- ============================================================

ALTER TABLE tenants ADD COLUMN email_verified_at TIMESTAMPTZ;

UPDATE tenants SET email_verified_at = created_at WHERE email_verified_at IS NULL;
