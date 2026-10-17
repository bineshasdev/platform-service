-- ============================================================
-- V7 — User profile image
--   Add profile_image_id (FK → tenant_files) and
--       profile_image_url (resolved/CDN URL) to the users table.
-- ============================================================

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS profile_image_id  UUID REFERENCES tenant_files(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS profile_image_url TEXT;

CREATE INDEX IF NOT EXISTS idx_users_profile_image ON users(profile_image_id);
