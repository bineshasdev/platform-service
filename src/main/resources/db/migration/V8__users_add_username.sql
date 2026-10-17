-- Add username to users table
-- Backfill from email (left of '@') as a sensible default for existing rows.
ALTER TABLE users ADD COLUMN username VARCHAR(100);

UPDATE users SET username = LOWER(SPLIT_PART(email, '@', 1)) WHERE username IS NULL;

ALTER TABLE users ALTER COLUMN username SET NOT NULL;

-- Unique per tenant (same as email constraint)
ALTER TABLE users ADD CONSTRAINT uq_tenant_username UNIQUE (tenant_id, username);

-- Case-insensitive index for the resolve-by-identifier lookup
CREATE INDEX idx_users_username_lower ON users (LOWER(username));
CREATE INDEX idx_users_email_lower    ON users (LOWER(email));
