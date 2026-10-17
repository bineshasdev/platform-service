-- ============================================================
-- V4 — users.role: drop DEFAULT and CHECK constraint
-- ============================================================

ALTER TABLE users
    ALTER COLUMN role DROP DEFAULT;

ALTER TABLE users
    DROP CONSTRAINT IF EXISTS users_role_check;
