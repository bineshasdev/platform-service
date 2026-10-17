-- ============================================================
-- V14 — CliniqAI healthcare built-in roles (PR-004 / FR-022)
--
-- Extends menu_role_access.role beyond the five generic platform
-- roles with the eleven CliniqAI provider-side persona roles, and
-- grants each new role access to the existing navigation menus.
--
-- users.role has no CHECK constraint (dropped in V4) and stores the
-- Keycloak realm-role name, so no change is needed there. The Keycloak
-- realm roles themselves are seeded on startup by KeycloakRoleInitializer.
-- ============================================================

-- ── Widen the menu_role_access CHECK constraint ──────────────────────
ALTER TABLE menu_role_access
    DROP CONSTRAINT IF EXISTS menu_role_access_role_check;

ALTER TABLE menu_role_access
    ADD CONSTRAINT menu_role_access_role_check
    CHECK (role IN (
        'OWNER','ADMIN','MANAGER','USER','VIEWER',
        'CLINIC_ADMIN','DOCTOR','NURSE','RECEPTIONIST',
        'LAB_ADMIN','LAB_TECHNICIAN','PHLEBOTOMIST','PHARMACIST',
        'MEDICAL_CODER','BILLING_STAFF','COMPLIANCE_OFFICER','PATIENT'
    ));

-- ── Menu access for the new roles ───────────────────────────────────

-- Base tier — every new role: dashboard, documents, reports, approvals
INSERT INTO menu_role_access (menu_item_id, role)
SELECT id, r.role
FROM menu_items,
     (VALUES
        ('CLINIC_ADMIN'),('DOCTOR'),('NURSE'),('RECEPTIONIST'),
        ('LAB_ADMIN'),('LAB_TECHNICIAN'),('PHLEBOTOMIST'),('PHARMACIST'),
        ('MEDICAL_CODER'),('BILLING_STAFF'),('COMPLIANCE_OFFICER')
     ) AS r(role)
WHERE code IN ('dashboard','documents','doc_upload','doc_list','doc_templates','reports','wf_approvals')
ON CONFLICT (menu_item_id, role) DO NOTHING;

-- Manager tier — AI Studio, Workflows, Integrations: tenant-admin-like roles
INSERT INTO menu_role_access (menu_item_id, role)
SELECT id, r.role
FROM menu_items,
     (VALUES ('CLINIC_ADMIN'),('LAB_ADMIN')) AS r(role)
WHERE code IN ('ai_studio','ai_processing','ai_models','workflows','wf_builder','wf_forms','wf_instances','integrations')
ON CONFLICT (menu_item_id, role) DO NOTHING;

-- Admin tier — tenant administration: CLINIC_ADMIN, LAB_ADMIN
INSERT INTO menu_role_access (menu_item_id, role)
SELECT id, r.role
FROM menu_items,
     (VALUES ('CLINIC_ADMIN'),('LAB_ADMIN')) AS r(role)
WHERE code IN ('admin','admin_users','admin_billing','admin_settings','admin_branding')
ON CONFLICT (menu_item_id, role) DO NOTHING;

-- Patient — self-service identity, not tenant staff: dashboard only
INSERT INTO menu_role_access (menu_item_id, role)
SELECT id, 'PATIENT'
FROM menu_items
WHERE code = 'dashboard'
ON CONFLICT (menu_item_id, role) DO NOTHING;
