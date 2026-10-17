-- ============================================================
-- V15 — Applications + RBAC foundation (roles, privileges, assignments)
--
-- Prerequisite for the menu-management model in V16.
--
-- Introduces:
--   applications     the frontend surfaces a user can log in to
--   privileges       the permission catalogue (FR-022)
--   roles            system roles (tenant_id NULL) + tenant custom roles (PR-004)
--   role_privileges  which privileges a role composes
--   user_roles       role assignment, optionally location-scoped and time-bounded
--
-- The UserRole / PlatformRole Java enums remain, but only as the seed
-- vocabulary: `roles` is now the source of truth for what a role IS,
-- and `roles.platform_role` records the Keycloak realm role it maps to.
-- ============================================================

-- ══ 1. Applications ═══════════════════════════════════════════════════
CREATE TABLE applications (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    code        TEXT        UNIQUE NOT NULL,
    name        TEXT        NOT NULL,
    description TEXT,
    base_url    TEXT,
    icon        TEXT,
    sort_order  INT         NOT NULL DEFAULT 0,
    is_active   BOOLEAN     NOT NULL DEFAULT true,
    metadata    JSONB       NOT NULL DEFAULT '{}',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID
);

COMMENT ON TABLE applications IS
    'Frontend surfaces. A user resolves a different menu tree per application.';

INSERT INTO applications (code, name, description, sort_order) VALUES
    ('PROVIDER_CONSOLE', 'Provider Console', 'Clinic, lab and pharmacy staff web console (cliniqai-ui)', 1),
    ('PATIENT_PORTAL',   'Patient Portal',   'Patient web and mobile self-service (M15)',                 2),
    ('KIOSK',            'Kiosk',            'In-clinic self check-in kiosk (M16)',                       3),
    ('PLATFORM_ADMIN',   'Platform Admin',   'CliniqAI operator console (platform-ui)',                   4);

-- ══ 2. Privilege catalogue ════════════════════════════════════════════
CREATE TABLE privileges (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    code        TEXT        UNIQUE NOT NULL,
    name        TEXT        NOT NULL,
    description TEXT,
    category    TEXT        NOT NULL,
    module_code TEXT,
    -- Reading/writing under this privilege touches PHI, so the ABAC
    -- care-relationship and consent layers (FR-020) must also be evaluated.
    is_phi      BOOLEAN     NOT NULL DEFAULT false,
    is_active   BOOLEAN     NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_privileges_category ON privileges(category);
CREATE INDEX idx_privileges_module   ON privileges(module_code);

COMMENT ON TABLE privileges IS
    'Permission catalogue the custom-role builder composes from (FR-022).';

INSERT INTO privileges (code, name, category, module_code, is_phi) VALUES
    -- Platform / generic
    ('dashboard.view',      'View dashboard',            'PLATFORM',    NULL,  false),
    ('document.view',       'View documents',            'PLATFORM',    NULL,  false),
    ('document.create',     'Upload documents',          'PLATFORM',    NULL,  false),
    ('document.edit',       'Edit documents',            'PLATFORM',    NULL,  false),
    ('document.delete',     'Delete documents',          'PLATFORM',    NULL,  false),
    ('report.view',         'View reports',              'PLATFORM',    NULL,  false),
    ('workflow.view',       'View workflows',            'WORKFLOW',    NULL,  false),
    ('workflow.manage',     'Build and edit workflows',  'WORKFLOW',    NULL,  false),
    ('workflow.approve',    'Action approval tasks',     'WORKFLOW',    NULL,  false),
    ('ai.use',              'Use AI features',           'AI',          'M18', false),
    ('ai.manage',           'Configure AI models',       'AI',          'M18', false),
    ('integration.manage',  'Manage integrations',       'INTEGRATION', 'M19', false),
    -- Tenant administration
    ('user.view',           'View users',                'ADMIN',       'M01', false),
    ('user.invite',         'Invite users',              'ADMIN',       'M01', false),
    ('user.manage',         'Manage users',              'ADMIN',       'M01', false),
    ('role.manage',         'Create and assign roles',   'ADMIN',       'M01', false),
    ('settings.manage',     'Manage tenant settings',    'ADMIN',       'M01', false),
    ('branding.manage',     'Manage branding',           'ADMIN',       'M01', false),
    ('menu.assign',         'Assign menus to roles/users','ADMIN',      'M01', false),
    -- Patient master index
    ('patient.register',    'Register patients',         'PATIENT',     'M02', false),
    ('patient.view',        'View patient records',      'PATIENT',     'M02', true),
    ('patient.merge',       'Merge / un-merge patients', 'PATIENT',     'M02', true),
    ('patient.export',      'Export patient data',       'PATIENT',     'M02', true),
    -- Scheduling
    ('appointment.view',    'View appointments',         'SCHEDULING',  'M04', false),
    ('appointment.book',    'Book appointments',         'SCHEDULING',  'M04', false),
    ('appointment.reschedule','Reschedule / cancel',     'SCHEDULING',  'M04', false),
    -- Clinical
    ('encounter.view',      'View encounters',           'CLINICAL',    'M05', true),
    ('encounter.chart',     'Chart an encounter',        'CLINICAL',    'M05', true),
    ('encounter.sign',      'Sign a clinical note',      'CLINICAL',    'M05', true),
    ('vitals.record',       'Record vitals',             'CLINICAL',    'M08', true),
    -- Prescribing / pharmacy
    ('prescription.create', 'Create prescriptions',      'PRESCRIPTION','M06', true),
    ('prescription.sign',   'Sign prescriptions',        'PRESCRIPTION','M06', true),
    ('prescription.dispense','Dispense prescriptions',   'PHARMACY',    'M12', true),
    -- Diagnostics
    ('lab.order',           'Place lab orders',          'LAB',         'M07', true),
    ('lab.result.upload',   'Upload lab results',        'LAB',         'M07', true),
    ('lab.result.view',     'View lab results',          'LAB',         'M07', true),
    -- Coding & billing
    ('coding.suggest',      'View code suggestions',     'BILLING',     'M09', false),
    ('coding.assign',       'Assign diagnostic codes',   'BILLING',     'M09', false),
    ('invoice.view',        'View invoices',             'BILLING',     'M10', false),
    ('invoice.create',      'Create invoices',           'BILLING',     'M10', false),
    ('payment.collect',     'Collect payments',          'BILLING',     'M10', false),
    ('claim.manage',        'Manage TPA / PMJAY claims', 'BILLING',     'M10', false),
    ('billing.view',        'View billing and plan',     'BILLING',     'M10', false),
    ('billing.manage',      'Manage subscription',       'BILLING',     'M10', false),
    -- Compliance
    ('audit.view',          'View audit logs',           'COMPLIANCE',  'M20', false),
    ('consent.manage',      'Manage patient consent',    'COMPLIANCE',  'M20', true),
    ('compliance.monitor',  'Monitor access anomalies',  'COMPLIANCE',  'M20', false),
    ('breakglass.access',   'Break-glass emergency access','COMPLIANCE','M20', true);

-- ══ 3. Roles ══════════════════════════════════════════════════════════
CREATE TABLE roles (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    -- NULL = platform-wide system role; non-NULL = tenant custom role (PR-004)
    tenant_id      UUID        REFERENCES tenants(id) ON DELETE CASCADE,
    code           TEXT        NOT NULL,
    name           TEXT        NOT NULL,
    description    TEXT,
    -- Optional privilege inheritance: a custom role may extend a system role.
    parent_role_id UUID        REFERENCES roles(id) ON DELETE SET NULL,
    -- Keycloak realm role this role maps to, for token/authority sync.
    platform_role  TEXT,
    is_system      BOOLEAN     NOT NULL DEFAULT false,
    is_assignable  BOOLEAN     NOT NULL DEFAULT true,
    -- PR-006: MFA is mandatory for any role with clinical write access.
    requires_mfa   BOOLEAN     NOT NULL DEFAULT false,
    sort_order     INT         NOT NULL DEFAULT 0,
    is_active      BOOLEAN     NOT NULL DEFAULT true,
    metadata       JSONB       NOT NULL DEFAULT '{}',
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by     UUID,
    updated_by     UUID,
    CONSTRAINT chk_roles_no_self_parent CHECK (parent_role_id IS NULL OR parent_role_id <> id),
    -- A system role is never owned by a tenant, and vice versa.
    CONSTRAINT chk_roles_system_global  CHECK (NOT is_system OR tenant_id IS NULL)
);

-- Partial unique indexes: plain UNIQUE would not dedupe system roles,
-- because NULL tenant_id never equals NULL in Postgres.
CREATE UNIQUE INDEX uq_roles_system_code ON roles(code)            WHERE tenant_id IS NULL;
CREATE UNIQUE INDEX uq_roles_tenant_code ON roles(tenant_id, code) WHERE tenant_id IS NOT NULL;
CREATE INDEX        idx_roles_tenant     ON roles(tenant_id);

COMMENT ON TABLE roles IS
    'System roles (tenant_id NULL, seeded from the UserRole enum) plus tenant custom roles.';

INSERT INTO roles (code, name, description, platform_role, is_system, requires_mfa, sort_order) VALUES
    ('OWNER',              'Owner',              'Tenant owner — full access',                        'tenantadmin',        true, true,   1),
    ('ADMIN',              'Administrator',      'Tenant administrator',                              'tenantadmin',        true, true,   2),
    ('MANAGER',            'Manager',            'Operational manager',                               'tenantadmin',        true, false,  3),
    ('USER',               'User',               'Standard authenticated user',                       'user',               true, false,  4),
    ('VIEWER',             'Viewer',             'Read-only access',                                  'guest',              true, false,  5),
    ('CLINIC_ADMIN',       'Clinic Admin',       'Runs a clinic tenant: staff, roles, schedules',     'clinic_admin',       true, true,  10),
    ('DOCTOR',             'Doctor',             'Licensed practitioner — charting and prescribing',  'doctor',             true, true,  11),
    ('NURSE',              'Nurse',              'Vitals, encounter assistance, chronic monitoring',  'nurse',              true, true,  12),
    ('RECEPTIONIST',       'Receptionist',       'Front desk — registration, booking, payments',      'receptionist',       true, false, 13),
    ('LAB_ADMIN',          'Lab Admin',          'Runs a lab tenant: catalog, pricing, TAT',          'lab_admin',          true, true,  14),
    ('LAB_TECHNICIAN',     'Lab Technician',     'Processes samples and uploads results',             'lab_technician',     true, true,  15),
    ('PHLEBOTOMIST',       'Phlebotomist',       'Home sample collection',                            'phlebotomist',       true, false, 16),
    ('PHARMACIST',         'Pharmacist',         'E-prescription queue, dispensing, substitutions',   'pharmacist',         true, true,  17),
    ('MEDICAL_CODER',      'Medical Coder',      'Assigns diagnostic and procedure codes',            'medical_coder',      true, false, 18),
    ('BILLING_STAFF',      'Billing Staff',      'Charge capture, invoicing, collections, claims',    'billing_staff',      true, false, 19),
    ('COMPLIANCE_OFFICER', 'Compliance Officer', 'DPO — access anomalies, consent, breach workflow',  'compliance_officer', true, false, 20),
    ('PATIENT',            'Patient',            'Patient self-service identity',                     'patient',            true, false, 30);

-- ══ 4. Role → privilege composition ═══════════════════════════════════
CREATE TABLE role_privileges (
    role_id      UUID        NOT NULL REFERENCES roles(id)      ON DELETE CASCADE,
    privilege_id UUID        NOT NULL REFERENCES privileges(id) ON DELETE CASCADE,
    granted_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    granted_by   UUID,
    PRIMARY KEY (role_id, privilege_id)
);

CREATE INDEX idx_role_privileges_privilege ON role_privileges(privilege_id);

-- OWNER holds every privilege.
INSERT INTO role_privileges (role_id, privilege_id)
SELECT r.id, p.id FROM roles r, privileges p
WHERE r.tenant_id IS NULL AND r.code = 'OWNER';

-- ADMIN holds everything except break-glass, which is deliberately narrow.
INSERT INTO role_privileges (role_id, privilege_id)
SELECT r.id, p.id FROM roles r, privileges p
WHERE r.tenant_id IS NULL AND r.code = 'ADMIN' AND p.code <> 'breakglass.access';

-- Everything else composes explicitly.
INSERT INTO role_privileges (role_id, privilege_id)
SELECT r.id, p.id
FROM roles r
JOIN (VALUES
    ('MANAGER', ARRAY['dashboard.view','document.view','document.create','document.edit','document.delete',
                      'report.view','workflow.view','workflow.manage','workflow.approve','ai.use','user.view']),
    ('USER', ARRAY['dashboard.view','document.view','document.create','document.edit','report.view',
                   'workflow.approve']),
    ('VIEWER', ARRAY['dashboard.view','document.view','report.view']),
    ('CLINIC_ADMIN', ARRAY['dashboard.view','document.view','document.create','document.edit','document.delete',
                           'report.view','workflow.view','workflow.manage','workflow.approve','ai.use',
                           'integration.manage','user.view','user.invite','user.manage','role.manage',
                           'settings.manage','branding.manage','menu.assign','billing.view','audit.view',
                           'patient.register','patient.view','appointment.view','appointment.book',
                           'appointment.reschedule']),
    ('DOCTOR', ARRAY['dashboard.view','document.view','report.view','patient.view','appointment.view',
                     'encounter.view','encounter.chart','encounter.sign','vitals.record',
                     'prescription.create','prescription.sign','lab.order','lab.result.view',
                     'coding.suggest','ai.use']),
    ('NURSE', ARRAY['dashboard.view','document.view','patient.view','appointment.view','encounter.view',
                    'vitals.record','lab.result.view']),
    ('RECEPTIONIST', ARRAY['dashboard.view','document.view','patient.register','patient.view',
                           'appointment.view','appointment.book','appointment.reschedule',
                           'invoice.create','invoice.view','payment.collect']),
    ('LAB_ADMIN', ARRAY['dashboard.view','document.view','document.create','document.edit','report.view',
                        'user.view','user.invite','user.manage','settings.manage','menu.assign',
                        'billing.view','patient.view','lab.order','lab.result.upload','lab.result.view',
                        'invoice.create','invoice.view','integration.manage']),
    ('LAB_TECHNICIAN', ARRAY['dashboard.view','document.view','patient.view',
                             'lab.result.upload','lab.result.view']),
    ('PHLEBOTOMIST', ARRAY['dashboard.view','patient.view','appointment.view','lab.result.view']),
    ('PHARMACIST', ARRAY['dashboard.view','document.view','patient.view','prescription.dispense',
                         'invoice.create','invoice.view','payment.collect']),
    ('MEDICAL_CODER', ARRAY['dashboard.view','report.view','encounter.view','coding.suggest','coding.assign']),
    ('BILLING_STAFF', ARRAY['dashboard.view','report.view','patient.view','invoice.view','invoice.create',
                            'payment.collect','claim.manage','billing.view']),
    ('COMPLIANCE_OFFICER', ARRAY['dashboard.view','report.view','audit.view','consent.manage',
                                 'compliance.monitor']),
    ('PATIENT', ARRAY['dashboard.view','appointment.view','appointment.book','appointment.reschedule',
                      'lab.result.view','patient.export'])
) AS m(role_code, privs) ON m.role_code = r.code
JOIN privileges p ON p.code = ANY(m.privs)
WHERE r.tenant_id IS NULL
ON CONFLICT DO NOTHING;

-- ══ 5. User → role assignment ═════════════════════════════════════════
CREATE TABLE user_roles (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id     UUID        NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    -- Optional location scope (PR-003: access scoped to tenant and, optionally, a location).
    location_id UUID,
    is_primary  BOOLEAN     NOT NULL DEFAULT false,
    valid_from  TIMESTAMPTZ NOT NULL DEFAULT now(),
    valid_to    TIMESTAMPTZ,
    assigned_by UUID,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_user_roles_validity CHECK (valid_to IS NULL OR valid_to > valid_from)
);

CREATE UNIQUE INDEX uq_user_roles_global  ON user_roles(user_id, role_id) WHERE location_id IS NULL;
CREATE UNIQUE INDEX uq_user_roles_scoped  ON user_roles(user_id, role_id, location_id) WHERE location_id IS NOT NULL;
-- Exactly one primary role per user.
CREATE UNIQUE INDEX uq_user_roles_primary ON user_roles(user_id) WHERE is_primary;
CREATE INDEX        idx_user_roles_role   ON user_roles(role_id);

COMMENT ON TABLE user_roles IS
    'Multi-role assignment. users.role remains a denormalised cache of the primary role.';

-- Backfill the primary role from the existing single users.role column.
-- users.role holds the Keycloak realm-role name, so the mapping is explicit.
INSERT INTO user_roles (user_id, role_id, is_primary)
SELECT u.id, r.id, true
FROM users u
JOIN (VALUES
        ('superadmin',         'OWNER'),
        ('tenantadmin',        'OWNER'),
        ('clinic_admin',       'CLINIC_ADMIN'),
        ('doctor',             'DOCTOR'),
        ('nurse',              'NURSE'),
        ('receptionist',       'RECEPTIONIST'),
        ('lab_admin',          'LAB_ADMIN'),
        ('lab_technician',     'LAB_TECHNICIAN'),
        ('phlebotomist',       'PHLEBOTOMIST'),
        ('pharmacist',         'PHARMACIST'),
        ('medical_coder',      'MEDICAL_CODER'),
        ('billing_staff',      'BILLING_STAFF'),
        ('compliance_officer', 'COMPLIANCE_OFFICER'),
        ('patient',            'PATIENT'),
        ('user',               'USER'),
        ('guest',              'VIEWER')
     ) AS m(platform_role, role_code) ON m.platform_role = u.role
JOIN roles r ON r.tenant_id IS NULL AND r.code = m.role_code
ON CONFLICT DO NOTHING;
