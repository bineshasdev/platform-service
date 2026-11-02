-- ============================================================
-- V34 — Retire the old access model (authorization architecture, phase 3)
--
-- Menu items name their privilege by the authorization model's code. The old
-- roles, privileges, role_privileges, user_roles and users.role are dropped.
-- See Docs/design/authorization-architecture.md §20.4.
-- ============================================================

-- ── Menu items: privilege by code ────────────────────────────────────
ALTER TABLE menu_items ADD COLUMN required_privilege TEXT REFERENCES authz_privileges(code) ON UPDATE CASCADE;

UPDATE menu_items m
   SET required_privilege = ap.code
  FROM privileges p
  JOIN authz_privileges ap ON ap.legacy_code = p.code
 WHERE p.id = m.required_privilege_id;

-- An item that named a privilege with no equivalent would silently become public. Refuse to proceed.
DO $$
DECLARE orphans INT;
BEGIN
    SELECT count(*) INTO orphans FROM menu_items WHERE required_privilege_id IS NOT NULL AND required_privilege IS NULL;
    IF orphans > 0 THEN
        RAISE EXCEPTION 'V34: % menu item(s) require a privilege with no authz equivalent', orphans;
    END IF;
END $$;

DROP INDEX IF EXISTS idx_menu_items_privilege;
CREATE INDEX idx_menu_items_required_privilege ON menu_items(required_privilege);
ALTER TABLE menu_items DROP COLUMN required_privilege_id;

-- ── Stored drafts, snapshots and manifests name privileges by their old codes ──
DO $$
DECLARE m RECORD;
BEGIN
    FOR m IN SELECT legacy_code AS old_code, code AS new_code FROM authz_privileges
              WHERE legacy_code IS NOT NULL AND legacy_code <> code LOOP
        UPDATE menu_change_sets
           SET ops = replace(ops::text, '"requiredPrivilege": "' || m.old_code || '"', '"requiredPrivilege": "' || m.new_code || '"')::jsonb
         WHERE ops::text LIKE '%"requiredPrivilege": "' || m.old_code || '"%';
        UPDATE menu_snapshots
           SET state = replace(state::text, '"requiredPrivilege": "' || m.old_code || '"', '"requiredPrivilege": "' || m.new_code || '"')::jsonb
         WHERE state::text LIKE '%"requiredPrivilege": "' || m.old_code || '"%';
        UPDATE menu_module_manifests
           SET manifest = replace(manifest::text, '"requiredPrivilege": "' || m.old_code || '"', '"requiredPrivilege": "' || m.new_code || '"')::jsonb
         WHERE manifest::text LIKE '%"requiredPrivilege": "' || m.old_code || '"%';
    END LOOP;
END $$;

-- A module's privileges are now those its service owns (authz_privileges.owner_service); the old
-- module codes (M01..M20) no longer mean anything.
UPDATE menu_modules SET privilege_module_code = NULL WHERE privilege_module_code IS NOT NULL;

-- ── The old model ────────────────────────────────────────────────────
DROP TABLE IF EXISTS user_roles;
DROP TABLE IF EXISTS role_privileges;
DROP TABLE IF EXISTS roles;
DROP TABLE IF EXISTS privileges;

DROP INDEX IF EXISTS idx_users_role;
ALTER TABLE users DROP COLUMN IF EXISTS role;

-- Nothing refers to the old codes now.
ALTER TABLE authz_privileges DROP COLUMN legacy_code;
