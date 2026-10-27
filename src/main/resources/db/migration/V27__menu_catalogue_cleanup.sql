-- ============================================================
-- V27 — Catalogue clean-up (Stage 8c, Docs/design/menu-catalogue.md E6, decision C3)
--   * retire the V5 FlowCore menu items (soft delete)
--   * re-key the V16 patient-portal items from pp_* to pp.*
-- ============================================================

-- 1. Retire the V5 items. They are soft-deleted, so every resolution query
--    already ignores them and published snapshots stay readable. uq_menu_items_app_code
--    still counts soft-deleted rows, and the catalogue seed reuses codes such as
--    dashboard, reports and admin — so the retired rows move to a legacy.* code.
--    Their role/plan grants and tenant overrides stay on the (now invisible) rows.
UPDATE menu_items
   SET code       = 'legacy.' || code,
       path       = 'legacy.' || code,
       is_active  = false,
       deleted_at = now(),
       updated_at = now()
 WHERE tenant_id IS NULL
   AND parent_id IS NULL
   AND application_id = (SELECT id FROM applications WHERE code = 'PROVIDER_CONSOLE')
   AND code IN ('dashboard','documents','ai_studio','workflows','reports','integrations','admin');

UPDATE menu_items c
   SET code       = 'legacy.' || c.code,
       path       = p.path || '/legacy.' || c.code,
       is_active  = false,
       deleted_at = now(),
       updated_at = now()
  FROM menu_items p
 WHERE c.parent_id = p.id
   AND p.code LIKE 'legacy.%'
   AND c.code IN ('doc_upload','doc_list','doc_templates','ai_processing','ai_models',
                  'wf_builder','wf_forms','wf_instances','wf_approvals',
                  'admin_users','admin_billing','admin_settings','admin_branding');

-- 2. Patient portal: pp_* -> pp.*, using the catalogue's codes. Three items are
--    renamed (prescriptions -> medicines, invoices -> payments, consent -> privacy),
--    with their routes. Ids are kept, so the PATIENT role grants and any tenant
--    overrides carry over.
UPDATE menu_items m
   SET code       = v.new_code,
       path       = v.new_code,
       route      = COALESCE(v.new_route, m.route),
       label      = COALESCE(v.new_label, m.label),
       updated_at = now()
  FROM (VALUES
        ('pp_home',          'pp.home',         NULL,         NULL),
        ('pp_appointments',  'pp.appointments', NULL,         'Appointments'),
        ('pp_records',       'pp.records',      NULL,         'Records'),
        ('pp_prescriptions', 'pp.medicines',    '/medicines', 'Medicines'),
        ('pp_invoices',      'pp.payments',     NULL,         NULL),
        ('pp_consent',       'pp.privacy',      '/privacy',   'Privacy & consent')
  ) AS v(old_code, new_code, new_route, new_label)
 WHERE m.code = v.old_code
   AND m.application_id = (SELECT id FROM applications WHERE code = 'PATIENT_PORTAL');
