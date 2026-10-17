-- ============================================================
-- V18 — doctor registration integrity
-- * The column-level UNIQUE on email is case-sensitive; registration lowercases
--   emails, but imports and manual inserts may not. Enforce case-insensitively.
-- * A council registration number identifies one practitioner per council.
--   Without this two accounts can claim the same licence number.
--
-- Doctors that existed before this migration are NOT marked contact-verified:
-- they are asked to verify email and mobile at their next sign-in.
-- ============================================================

CREATE UNIQUE INDEX uq_doctors_email_lower ON doctors (LOWER(email));

CREATE UNIQUE INDEX uq_doctors_council_registration
    ON doctors (LOWER(registration_council), LOWER(registration_number))
    WHERE registration_council IS NOT NULL AND registration_number IS NOT NULL;
