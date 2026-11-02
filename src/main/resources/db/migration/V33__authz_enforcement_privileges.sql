-- ============================================================
-- V33 — Privileges for enforcement everywhere (authorization architecture, phase 3)
--
-- Adds the privileges the newly guarded endpoints need and grants them to
-- the templates and platform roles. See Docs/design/authorization-architecture.md §20.
-- ============================================================

INSERT INTO authz_privileges
    (code, owner_service, name, tenant_types, phi, clinical_write, assignable_by_tenant, scope_kind, risk)
VALUES
    ('platform.storage.manage',      'platform',     'Manage storage backend and quota',   '{CLINIC,LAB,STORE}', false, false, true,  'TENANT',   'HIGH'),
    ('platform.operator.manage',     'platform',     'Manage platform operators',          '{}',                 false, false, false, 'PLATFORM', 'CRITICAL'),
    ('notification.message.view',    'notification', 'View own notifications and preferences', '{CLINIC,LAB,STORE}', false, false, true, 'SELF',  'LOW'),
    ('notification.template.manage', 'notification', 'Manage notification templates',      '{CLINIC,LAB,STORE}', false, false, true,  'TENANT',   'HIGH'),
    ('notification.event.send',      'notification', 'Trigger notification events',        '{CLINIC,LAB,STORE}', false, false, true,  'TENANT',   'HIGH');

-- Every member can see their own notifications.
INSERT INTO authz_role_template_privileges (template_id, privilege_code)
SELECT t.id, 'notification.message.view' FROM authz_role_templates t
ON CONFLICT DO NOTHING;

-- Tenant administrators run the tenant's storage, templates and event triggers.
INSERT INTO authz_role_template_privileges (template_id, privilege_code)
SELECT t.id, p.code
FROM authz_role_templates t
CROSS JOIN (VALUES ('platform.storage.manage'), ('notification.template.manage'), ('notification.event.send')) AS p(code)
WHERE t.is_admin
ON CONFLICT DO NOTHING;

-- Platform roles.
INSERT INTO authz_role_privilege_deltas (role_id, privilege_code, effect)
SELECT r.id, 'platform.operator.manage', 'GRANT'
FROM authz_roles r
WHERE r.tenant_id IS NULL AND r.code = 'super_admin'
ON CONFLICT DO NOTHING;
