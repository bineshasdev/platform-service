-- ============================================================
-- V32 — Navigation from privileges (authorization architecture, phase 2)
--
-- A menu item is visible to whoever holds its required privilege in the
-- new authorization model; there is no role-to-menu or group-to-menu
-- mapping any more, and user groups are removed.
-- See Docs/design/authorization-architecture.md §19.
-- ============================================================

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
