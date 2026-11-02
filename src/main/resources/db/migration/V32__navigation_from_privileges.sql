-- ============================================================
-- V32 — Navigation from privileges (authorization architecture, phase 2)
--
-- A menu item is visible to whoever holds its required privilege in the
-- new authorization model; there is no role-to-menu or group-to-menu
-- mapping any more, and user groups are removed.
-- See Docs/design/authorization-architecture.md §19.
-- ============================================================

-- Stored drafts and history may hold the retired operations, which could no longer be read
-- back. Remove just those operations from the lists; everything else stays.
UPDATE menu_change_sets
   SET ops = COALESCE((SELECT jsonb_agg(o) FROM jsonb_array_elements(ops) AS o
                       WHERE o->>'op' NOT IN ('SET_ROLES', 'SET_GROUP_ACCESS')), '[]'::jsonb)
 WHERE ops::text LIKE '%"SET_ROLES"%' OR ops::text LIKE '%"SET_GROUP_ACCESS"%';

-- The seeded "User groups" screen needs the retired group.manage privilege.
UPDATE menu_items
   SET is_active = false, deleted_at = now()
 WHERE code = 'settings.groups' AND tenant_id IS NULL AND deleted_at IS NULL;

-- ── Groups ───────────────────────────────────────────────────────────
-- (their version triggers go with the tables)
DROP TABLE IF EXISTS menu_group_access;
DROP TABLE IF EXISTS user_group_members;
DROP TABLE IF EXISTS user_groups;
DROP FUNCTION IF EXISTS trg_menu_group_access_bump();

DELETE FROM role_privileges WHERE privilege_id IN (SELECT id FROM privileges WHERE code = 'group.manage');
DELETE FROM privileges WHERE code = 'group.manage';

-- ── Role-to-menu mapping ─────────────────────────────────────────────
DROP TABLE IF EXISTS menu_role_access;
DROP FUNCTION IF EXISTS trg_menu_role_access_bump();

-- Looked up when the catalogue compiler translates an item's old privilege code.
CREATE INDEX IF NOT EXISTS idx_authz_privileges_legacy ON authz_privileges(legacy_code);
