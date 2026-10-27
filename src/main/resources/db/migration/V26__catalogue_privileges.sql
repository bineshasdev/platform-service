-- ============================================================
-- V26 — Privileges needed by the predefined menu catalogue (Stage 8b)
-- Docs/design/menu-catalogue.md §8. module_code follows the PRD module
-- numbering (M03 Scheduling, M11 Lab Network, M12 Pharmacy Network,
-- M13 Referral Network, M17 Telemedicine, M19 Interoperability).
-- ============================================================

INSERT INTO privileges (code, name, category, module_code, is_phi) VALUES
    ('schedule.manage',          'Manage appointment types, availability and resources', 'SCHEDULING',  'M03', false),
    ('referral.view',            'View referrals and the provider directory',            'REFERRAL',    'M13', true),
    ('referral.manage',          'Create and respond to referrals',                      'REFERRAL',    'M13', true),
    ('telehealth.consult',       'Run telehealth consultations',                         'TELEMEDICINE','M17', true),
    ('lab.sample.manage',        'Manage lab samples and home collection',               'LAB',         'M11', true),
    ('lab.catalog.manage',       'Manage the lab test catalogue, pricing and TAT',       'LAB',         'M11', false),
    ('pharmacy.inventory.manage','Manage pharmacy inventory, catalogue and delivery',    'PHARMACY',    'M12', false),
    ('data.import',              'Bulk import data',                                     'INTEGRATION', 'M19', false),
    ('data.export',              'Bulk export data',                                     'INTEGRATION', 'M19', true);

-- OWNER holds every privilege; ADMIN everything except break-glass (V15).
INSERT INTO role_privileges (role_id, privilege_id)
SELECT r.id, p.id
FROM roles r, privileges p
WHERE r.tenant_id IS NULL AND r.code IN ('OWNER', 'ADMIN')
  AND p.code IN ('schedule.manage','referral.view','referral.manage','telehealth.consult','lab.sample.manage',
                 'lab.catalog.manage','pharmacy.inventory.manage','data.import','data.export')
ON CONFLICT DO NOTHING;

-- Everyone else, as listed in the catalogue.
INSERT INTO role_privileges (role_id, privilege_id)
SELECT r.id, p.id
FROM roles r
JOIN (VALUES
    ('MANAGER',        ARRAY['schedule.manage']),
    ('CLINIC_ADMIN',   ARRAY['schedule.manage','referral.view','data.import']),
    ('DOCTOR',         ARRAY['referral.view','referral.manage','telehealth.consult']),
    ('NURSE',          ARRAY['referral.view']),
    ('LAB_ADMIN',      ARRAY['lab.sample.manage','lab.catalog.manage']),
    ('LAB_TECHNICIAN', ARRAY['lab.sample.manage']),
    ('PHLEBOTOMIST',   ARRAY['lab.sample.manage']),
    ('PHARMACIST',     ARRAY['pharmacy.inventory.manage'])
) AS m(role_code, privs) ON m.role_code = r.code
JOIN privileges p ON p.code = ANY(m.privs)
WHERE r.tenant_id IS NULL
ON CONFLICT DO NOTHING;
