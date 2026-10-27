-- ============================================================
-- V22 — Temporary and time-bound menus (menu stage 4)
--
--   source            who owns the row's lifecycle: PLATFORM (authored via draft),
--                     MODULE (registered by a service, stage 6), TEMPORARY (live API)
--   lifecycle_status  DRAFT | SCHEDULED | ACTIVE | DISABLED | CANCELLED | EXPIRED
--   starts_at/ends_at UTC instants, end exclusive
--   audience_mode     RESTRICTED  needs a role / user / group grant
--                     ALL_ELIGIBLE  everyone who passes plan, feature and privilege gates
--
-- Visibility is decided at request time from the window, never from the status
-- alone, so a stalled scheduler cannot leave an expired menu on screen. The
-- scheduler only keeps lifecycle_status truthful and writes the audit trail.
-- Expired rows stay in the table.
-- ============================================================

ALTER TABLE menu_items
    ADD COLUMN source           TEXT        NOT NULL DEFAULT 'PLATFORM'
                                    CHECK (source IN ('PLATFORM', 'MODULE', 'TEMPORARY')),
    ADD COLUMN lifecycle_status TEXT        NOT NULL DEFAULT 'ACTIVE'
                                    CHECK (lifecycle_status IN
                                           ('DRAFT', 'SCHEDULED', 'ACTIVE', 'DISABLED', 'CANCELLED', 'EXPIRED')),
    ADD COLUMN starts_at        TIMESTAMPTZ,
    ADD COLUMN ends_at          TIMESTAMPTZ,
    ADD COLUMN badge            TEXT,
    ADD COLUMN priority         INT         NOT NULL DEFAULT 0,
    ADD COLUMN audience_mode    TEXT        NOT NULL DEFAULT 'RESTRICTED'
                                    CHECK (audience_mode IN ('ALL_ELIGIBLE', 'RESTRICTED'));

ALTER TABLE menu_items
    ADD CONSTRAINT chk_menu_items_window
        CHECK (starts_at IS NULL OR ends_at IS NULL OR ends_at > starts_at),
    ADD CONSTRAINT chk_menu_items_temporary_window
        CHECK (source <> 'TEMPORARY' OR (starts_at IS NOT NULL AND ends_at IS NOT NULL));

-- The scheduler scans only live temporary rows.
CREATE INDEX idx_menu_items_temporary_due
    ON menu_items (lifecycle_status, starts_at, ends_at)
    WHERE source = 'TEMPORARY';
