-- ============================================================
-- V29 — Role/menu corrections found by the per-role check (Stage 8e)
--   * CLINIC_ADMIN runs claims for the clinic          -> claim.manage
--   * MEDICAL_CODER works the AI code suggestions      -> ai.use
--   * Orders & results is for anyone who can review results (ordering stays gated inside the
--     screen by lab.order)                              -> `orders` requires lab.result.view
-- Docs/design/menu-catalogue.md §11.
-- ============================================================

INSERT INTO role_privileges (role_id, privilege_id)
SELECT r.id, p.id
  FROM roles r, privileges p
 WHERE r.tenant_id IS NULL
   AND ((r.code = 'CLINIC_ADMIN'  AND p.code = 'claim.manage')
     OR (r.code = 'MEDICAL_CODER' AND p.code = 'ai.use'))
ON CONFLICT DO NOTHING;

UPDATE menu_items m
   SET required_privilege_id = (SELECT id FROM privileges WHERE code = 'lab.result.view'),
       updated_at = now()
  FROM applications a
 WHERE a.id = m.application_id AND a.code = 'PROVIDER_CONSOLE'
   AND m.code = 'orders' AND m.tenant_id IS NULL AND m.deleted_at IS NULL;

-- Role access is a snapshot of who held each privilege (V28): add the roles that hold one now.
INSERT INTO menu_role_access (menu_item_id, role_id)
SELECT m.id, r.id
  FROM menu_items m
  JOIN applications a ON a.id = m.application_id AND a.code = 'PROVIDER_CONSOLE'
  JOIN role_privileges rp ON rp.privilege_id = m.required_privilege_id
  JOIN roles r ON r.id = rp.role_id AND r.tenant_id IS NULL AND r.code <> 'PATIENT'
 WHERE m.tenant_id IS NULL AND m.deleted_at IS NULL AND m.source = 'PLATFORM'
ON CONFLICT DO NOTHING;
