-- ============================================================
-- V31 — Authorization core (authorization architecture, phase 1)
--
--   authz_privileges               catalogue: <service>.<resource>.<action>
--   authz_role_templates           platform roles per tenant type
--   authz_role_template_privileges what each template grants
--   authz_roles                    TEMPLATE / DERIVED / CUSTOM per tenant, PLATFORM for operators
--   authz_role_privilege_deltas    GRANT / DENY on top of a role's template
--   authz_memberships              a user in a tenant (tenant NULL = platform)
--   authz_role_assignments         one primary + extra roles per membership
--
-- Runs beside the old model (roles, privileges, role_privileges, user_roles,
-- menu tables), which is untouched. Phase 2 moves navigation and phase 3
-- moves endpoints onto these tables; then the old ones are dropped.
-- See Docs/design/authorization-architecture.md §18.
-- ============================================================

-- ══ 1. Catalogue ══════════════════════════════════════════════════════

CREATE TABLE authz_privileges (
    code                 TEXT        PRIMARY KEY
                             CHECK (code ~ '^[a-z][a-z0-9_]*\.[a-z][a-z0-9_]*\.[a-z][a-z0-9_]*$'),
    owner_service        TEXT        NOT NULL,
    name                 TEXT        NOT NULL,
    description          TEXT,
    -- Tenant types the privilege exists in. Empty = platform-only.
    tenant_types         TEXT[]      NOT NULL DEFAULT '{}'
                             CHECK (tenant_types <@ ARRAY['CLINIC','LAB','STORE']::TEXT[]),
    -- Plan feature the tenant must have enabled; NULL = every plan.
    feature_code         TEXT        REFERENCES features(code),
    scope_kind           TEXT        NOT NULL DEFAULT 'TENANT'
                             CHECK (scope_kind IN ('TENANT','LOCATION','SELF','PLATFORM')),
    phi                  BOOLEAN     NOT NULL DEFAULT false,
    clinical_write       BOOLEAN     NOT NULL DEFAULT false,
    assignable_by_tenant BOOLEAN     NOT NULL DEFAULT true,
    risk                 TEXT        NOT NULL DEFAULT 'LOW'
                             CHECK (risk IN ('LOW','HIGH','CRITICAL')),
    status               TEXT        NOT NULL DEFAULT 'ACTIVE'
                             CHECK (status IN ('ACTIVE','DEPRECATED')),
    -- The privileges.code this replaces; read by phases 2-3, then dropped.
    legacy_code          TEXT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- A service declares only its own namespace.
    CONSTRAINT chk_authz_privileges_owner
        CHECK (split_part(code, '.', 1) = owner_service),
    -- Tenant privileges name their types; platform-only ones name none and cannot be put in tenant roles.
    CONSTRAINT chk_authz_privileges_platform_only
        CHECK ((assignable_by_tenant AND cardinality(tenant_types) > 0)
            OR (NOT assignable_by_tenant AND cardinality(tenant_types) = 0 AND scope_kind = 'PLATFORM'))
);

CREATE INDEX idx_authz_privileges_owner ON authz_privileges(owner_service);

INSERT INTO authz_privileges
    (code, owner_service, name, tenant_types, phi, clinical_write, assignable_by_tenant, scope_kind, risk, legacy_code)
VALUES
    -- Platform: workspace, members, roles, settings
    ('platform.dashboard.view',       'platform', 'View dashboard',                   '{CLINIC,LAB,STORE}', false, false, true,  'TENANT',   'LOW',      'dashboard.view'),
    ('platform.document.view',        'platform', 'View documents',                   '{CLINIC,LAB,STORE}', false, false, true,  'TENANT',   'LOW',      'document.view'),
    ('platform.document.create',      'platform', 'Upload documents',                 '{CLINIC,LAB,STORE}', false, false, true,  'TENANT',   'LOW',      'document.create'),
    ('platform.document.edit',        'platform', 'Edit documents',                   '{CLINIC,LAB,STORE}', false, false, true,  'TENANT',   'LOW',      'document.edit'),
    ('platform.document.delete',      'platform', 'Delete documents',                 '{CLINIC,LAB,STORE}', false, false, true,  'TENANT',   'HIGH',     'document.delete'),
    ('platform.report.view',          'platform', 'View reports',                     '{CLINIC,LAB,STORE}', false, false, true,  'TENANT',   'LOW',      'report.view'),
    ('platform.member.view',          'platform', 'View members',                     '{CLINIC,LAB,STORE}', false, false, true,  'TENANT',   'LOW',      'user.view'),
    ('platform.member.invite',        'platform', 'Invite members',                   '{CLINIC,LAB,STORE}', false, false, true,  'TENANT',   'HIGH',     'user.invite'),
    ('platform.member.manage',        'platform', 'Manage members and their roles',   '{CLINIC,LAB,STORE}', false, false, true,  'TENANT',   'HIGH',     'user.manage'),
    ('platform.role.manage',          'platform', 'Create and edit roles',            '{CLINIC,LAB,STORE}', false, false, true,  'TENANT',   'HIGH',     'role.manage'),
    ('platform.settings.manage',      'platform', 'Manage tenant settings',           '{CLINIC,LAB,STORE}', false, false, true,  'TENANT',   'LOW',      'settings.manage'),
    ('platform.branding.manage',      'platform', 'Manage branding',                  '{CLINIC,LAB,STORE}', false, false, true,  'TENANT',   'LOW',      'branding.manage'),
    ('platform.navigation.manage',    'platform', 'Assign navigation to roles and users','{CLINIC,LAB,STORE}', false, false, true, 'TENANT',  'LOW',      'menu.assign'),
    ('platform.navigation.edit',      'platform', 'Edit navigation drafts',           '{CLINIC,LAB,STORE}', false, false, true,  'TENANT',   'LOW',      'menu.manage'),
    ('platform.navigation.publish',   'platform', 'Publish and roll back navigation', '{CLINIC,LAB,STORE}', false, false, true,  'TENANT',   'HIGH',     'menu.publish'),
    ('platform.subscription.view',    'platform', 'View billing and plan',            '{CLINIC,LAB,STORE}', false, false, true,  'TENANT',   'LOW',      'billing.view'),
    ('platform.subscription.manage',  'platform', 'Manage subscription',              '{CLINIC,LAB,STORE}', false, false, true,  'TENANT',   'HIGH',     'billing.manage'),
    -- Platform-only (operators)
    ('platform.tenant.view',          'platform', 'View tenants',                     '{}',                 false, false, false, 'PLATFORM', 'LOW',      NULL),
    ('platform.tenant.manage',        'platform', 'Manage tenants',                   '{}',                 false, false, false, 'PLATFORM', 'HIGH',     NULL),
    ('platform.tenant.verify',        'platform', 'Review and verify tenants',        '{}',                 false, false, false, 'PLATFORM', 'HIGH',     NULL),
    ('platform.template.manage',      'platform', 'Manage role templates',            '{}',                 false, false, false, 'PLATFORM', 'HIGH',     NULL),
    ('platform.support.session',      'platform', 'Open a support session in a tenant','{}',                false, false, false, 'PLATFORM', 'CRITICAL', NULL),
    -- Workflow
    ('workflow.definition.view',      'workflow', 'View workflows',                   '{CLINIC,LAB,STORE}', false, false, true,  'TENANT',   'LOW',      'workflow.view'),
    ('workflow.definition.manage',    'workflow', 'Build and edit workflows',         '{CLINIC,LAB,STORE}', false, false, true,  'TENANT',   'HIGH',     'workflow.manage'),
    ('workflow.task.approve',         'workflow', 'Action approval tasks',            '{CLINIC,LAB,STORE}', false, false, true,  'TENANT',   'LOW',      'workflow.approve'),
    -- AI
    ('ai.assistant.use',              'ai',       'Use AI features',                  '{CLINIC,LAB,STORE}', false, false, true,  'TENANT',   'LOW',      'ai.use'),
    ('ai.model.manage',               'ai',       'Configure AI models',              '{CLINIC,LAB,STORE}', false, false, true,  'TENANT',   'HIGH',     'ai.manage'),
    -- Integration
    ('integration.connection.manage', 'integration', 'Manage integrations',           '{CLINIC,LAB,STORE}', false, false, true,  'TENANT',   'HIGH',     'integration.manage'),
    ('integration.data.import',       'integration', 'Bulk import data',              '{CLINIC,LAB,STORE}', false, false, true,  'TENANT',   'HIGH',     'data.import'),
    ('integration.data.export',       'integration', 'Bulk export data',              '{CLINIC,LAB,STORE}', true,  false, true,  'TENANT',   'CRITICAL', 'data.export'),
    -- Patient master index
    ('patient.record.register',       'patient',  'Register patients',                '{CLINIC,LAB,STORE}', false, false, true,  'TENANT',   'LOW',      'patient.register'),
    ('patient.record.view',           'patient',  'View patient records',             '{CLINIC,LAB,STORE}', true,  false, true,  'TENANT',   'LOW',      'patient.view'),
    ('patient.record.merge',          'patient',  'Merge and un-merge patients',      '{CLINIC,LAB,STORE}', true,  false, true,  'TENANT',   'CRITICAL', 'patient.merge'),
    ('patient.record.export',         'patient',  'Export patient data',              '{CLINIC,LAB,STORE}', true,  false, true,  'TENANT',   'CRITICAL', 'patient.export'),
    -- Scheduling
    ('scheduling.appointment.view',   'scheduling', 'View appointments',              '{CLINIC,LAB}',       false, false, true,  'TENANT',   'LOW',      'appointment.view'),
    ('scheduling.appointment.book',   'scheduling', 'Book appointments',              '{CLINIC,LAB}',       false, false, true,  'TENANT',   'LOW',      'appointment.book'),
    ('scheduling.appointment.reschedule','scheduling','Reschedule and cancel',        '{CLINIC,LAB}',       false, false, true,  'TENANT',   'LOW',      'appointment.reschedule'),
    ('scheduling.calendar.manage',    'scheduling', 'Manage appointment types and availability','{CLINIC,LAB}', false, false, true, 'TENANT', 'LOW',     'schedule.manage'),
    -- Clinical
    ('clinical.encounter.view',       'clinical', 'View encounters',                  '{CLINIC}',           true,  false, true,  'TENANT',   'LOW',      'encounter.view'),
    ('clinical.encounter.chart',      'clinical', 'Chart an encounter',               '{CLINIC}',           true,  true,  true,  'TENANT',   'HIGH',     'encounter.chart'),
    ('clinical.encounter.sign',       'clinical', 'Sign a clinical note',             '{CLINIC}',           true,  true,  true,  'TENANT',   'HIGH',     'encounter.sign'),
    ('clinical.vitals.record',        'clinical', 'Record vitals',                    '{CLINIC}',           true,  true,  true,  'TENANT',   'LOW',      'vitals.record'),
    ('clinical.prescription.create',  'clinical', 'Create prescriptions',             '{CLINIC}',           true,  true,  true,  'TENANT',   'HIGH',     'prescription.create'),
    ('clinical.prescription.sign',    'clinical', 'Sign prescriptions',               '{CLINIC}',           true,  true,  true,  'TENANT',   'HIGH',     'prescription.sign'),
    -- Pharmacy
    ('pharmacy.prescription.dispense','pharmacy', 'Dispense prescriptions',           '{STORE}',            true,  true,  true,  'TENANT',   'HIGH',     'prescription.dispense'),
    ('pharmacy.inventory.manage',     'pharmacy', 'Manage inventory, catalogue and delivery','{STORE}',     false, false, true,  'TENANT',   'LOW',      'pharmacy.inventory.manage'),
    -- Lab
    ('lab.order.create',              'lab',      'Place lab orders',                 '{CLINIC,LAB}',       true,  false, true,  'TENANT',   'LOW',      'lab.order'),
    ('lab.result.enter',              'lab',      'Enter and upload lab results',     '{LAB}',              true,  true,  true,  'TENANT',   'HIGH',     'lab.result.upload'),
    ('lab.result.view',               'lab',      'View lab results',                 '{CLINIC,LAB}',       true,  false, true,  'TENANT',   'LOW',      'lab.result.view'),
    ('lab.sample.manage',             'lab',      'Manage samples and home collection','{LAB}',             true,  false, true,  'TENANT',   'LOW',      'lab.sample.manage'),
    ('lab.catalog.manage',            'lab',      'Manage test catalogue, pricing and TAT','{LAB}',         false, false, true,  'TENANT',   'LOW',      'lab.catalog.manage'),
    -- Billing
    ('billing.coding.suggest',        'billing',  'View code suggestions',            '{CLINIC}',           false, false, true,  'TENANT',   'LOW',      'coding.suggest'),
    ('billing.coding.assign',         'billing',  'Assign diagnostic codes',          '{CLINIC}',           false, false, true,  'TENANT',   'LOW',      'coding.assign'),
    ('billing.invoice.view',          'billing',  'View invoices',                    '{CLINIC,LAB,STORE}', false, false, true,  'TENANT',   'LOW',      'invoice.view'),
    ('billing.invoice.create',        'billing',  'Create invoices',                  '{CLINIC,LAB,STORE}', false, false, true,  'TENANT',   'LOW',      'invoice.create'),
    ('billing.payment.collect',       'billing',  'Collect payments',                 '{CLINIC,LAB,STORE}', false, false, true,  'TENANT',   'LOW',      'payment.collect'),
    ('billing.claim.manage',          'billing',  'Manage insurance claims',          '{CLINIC,LAB}',       false, false, true,  'TENANT',   'LOW',      'claim.manage'),
    -- Compliance
    ('compliance.audit.view',         'compliance', 'View audit logs',                '{CLINIC,LAB,STORE}', false, false, true,  'TENANT',   'LOW',      'audit.view'),
    ('compliance.consent.manage',     'compliance', 'Manage patient consent',         '{CLINIC,LAB,STORE}', true,  false, true,  'TENANT',   'HIGH',     'consent.manage'),
    ('compliance.access.monitor',     'compliance', 'Monitor access anomalies',       '{CLINIC,LAB,STORE}', false, false, true,  'TENANT',   'LOW',      'compliance.monitor'),
    ('compliance.breakglass.access',  'compliance', 'Break-glass emergency access',   '{CLINIC}',           true,  false, true,  'TENANT',   'CRITICAL', 'breakglass.access'),
    -- Referral and telehealth
    ('referral.request.view',         'referral', 'View referrals and the provider directory','{CLINIC}',  true,  false, true,  'TENANT',   'LOW',      'referral.view'),
    ('referral.request.manage',       'referral', 'Create and respond to referrals',  '{CLINIC}',           true,  false, true,  'TENANT',   'LOW',      'referral.manage'),
    ('telehealth.consultation.run',   'telehealth', 'Run telehealth consultations',   '{CLINIC}',           true,  false, true,  'TENANT',   'LOW',      'telehealth.consult');

-- ══ 2. Templates ═════════════════════════════════════════════════════

CREATE TABLE authz_role_templates (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_type      TEXT        NOT NULL CHECK (tenant_type IN ('CLINIC','LAB','STORE')),
    code             TEXT        NOT NULL CHECK (code ~ '^[a-z][a-z0-9_]{0,62}$'),
    name             TEXT        NOT NULL,
    description      TEXT,
    version          INT         NOT NULL DEFAULT 1,
    primary_eligible BOOLEAN     NOT NULL DEFAULT true,
    -- The template the tenant's creator gets. Exactly one per type.
    is_admin         BOOLEAN     NOT NULL DEFAULT false,
    -- What legacy users with an unmapped role get during backfill. Exactly one per type.
    is_fallback      BOOLEAN     NOT NULL DEFAULT false,
    sort_order       INT         NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_authz_role_templates UNIQUE (tenant_type, code)
);

CREATE UNIQUE INDEX uq_authz_role_templates_admin    ON authz_role_templates(tenant_type) WHERE is_admin;
CREATE UNIQUE INDEX uq_authz_role_templates_fallback ON authz_role_templates(tenant_type) WHERE is_fallback;

CREATE TABLE authz_role_template_privileges (
    template_id    UUID NOT NULL REFERENCES authz_role_templates(id) ON DELETE CASCADE,
    privilege_code TEXT NOT NULL REFERENCES authz_privileges(code) ON UPDATE CASCADE,
    PRIMARY KEY (template_id, privilege_code)
);

INSERT INTO authz_role_templates (tenant_type, code, name, description, is_admin, is_fallback, sort_order) VALUES
    ('CLINIC', 'clinic_admin',       'Clinic admin',       'Runs the clinic: members, roles, settings, schedules', true,  false, 10),
    ('CLINIC', 'doctor',             'Doctor',             'Licensed practitioner: charting, prescribing, ordering', false, false, 20),
    ('CLINIC', 'nurse',              'Nurse',              'Vitals, encounter assistance, monitoring',              false, false, 30),
    ('CLINIC', 'receptionist',       'Receptionist',       'Front desk: registration, booking, payments',           false, false, 40),
    ('CLINIC', 'billing_staff',      'Billing staff',      'Invoicing, collections, claims',                        false, false, 50),
    ('CLINIC', 'medical_coder',      'Medical coder',      'Diagnostic and procedure coding',                       false, false, 60),
    ('CLINIC', 'compliance_officer', 'Compliance officer', 'Audit, consent, access monitoring',                      false, false, 70),
    ('CLINIC', 'assistant',          'Assistant',          'Registration and booking support',                      false, true,  80),
    ('LAB',    'lab_admin',          'Lab admin',          'Runs the lab: catalogue, pricing, members, settings',   true,  false, 10),
    ('LAB',    'lab_technician',     'Lab technician',     'Processes samples and enters results',                  false, false, 20),
    ('LAB',    'phlebotomist',       'Phlebotomist',       'Sample collection, including home visits',              false, false, 30),
    ('LAB',    'front_desk',         'Front desk',         'Registration, orders, booking, payments',               false, true,  40),
    ('STORE',  'store_admin',        'Store admin',        'Runs the store: inventory, members, settings',          true,  false, 10),
    ('STORE',  'pharmacist',         'Pharmacist',         'Dispensing and inventory',                              false, false, 20),
    ('STORE',  'counter_staff',      'Counter staff',      'Sales and payments',                                    false, true,  30);

INSERT INTO authz_role_template_privileges (template_id, privilege_code)
SELECT t.id, unnest(m.privs)
FROM authz_role_templates t
JOIN (VALUES
    ('CLINIC', 'clinic_admin', ARRAY[
        'platform.dashboard.view','platform.document.view','platform.document.create','platform.document.edit',
        'platform.document.delete','platform.report.view','platform.member.view','platform.member.invite',
        'platform.member.manage','platform.role.manage','platform.settings.manage','platform.branding.manage',
        'platform.navigation.manage','platform.navigation.edit','platform.navigation.publish',
        'platform.subscription.view','platform.subscription.manage',
        'workflow.definition.view','workflow.definition.manage','workflow.task.approve','ai.assistant.use',
        'integration.connection.manage','integration.data.import','compliance.audit.view',
        'patient.record.register','patient.record.view','scheduling.appointment.view','scheduling.appointment.book',
        'scheduling.appointment.reschedule','scheduling.calendar.manage','referral.request.view','billing.claim.manage']),
    ('CLINIC', 'doctor', ARRAY[
        'platform.dashboard.view','platform.document.view','platform.report.view','patient.record.view',
        'scheduling.appointment.view','clinical.encounter.view','clinical.encounter.chart','clinical.encounter.sign',
        'clinical.vitals.record','clinical.prescription.create','clinical.prescription.sign','lab.order.create',
        'lab.result.view','billing.coding.suggest','ai.assistant.use','referral.request.view',
        'referral.request.manage','telehealth.consultation.run']),
    ('CLINIC', 'nurse', ARRAY[
        'platform.dashboard.view','platform.document.view','patient.record.view','scheduling.appointment.view',
        'clinical.encounter.view','clinical.vitals.record','lab.result.view','referral.request.view']),
    ('CLINIC', 'receptionist', ARRAY[
        'platform.dashboard.view','platform.document.view','patient.record.register','patient.record.view',
        'scheduling.appointment.view','scheduling.appointment.book','scheduling.appointment.reschedule',
        'billing.invoice.create','billing.invoice.view','billing.payment.collect']),
    ('CLINIC', 'billing_staff', ARRAY[
        'platform.dashboard.view','platform.report.view','patient.record.view','billing.invoice.view',
        'billing.invoice.create','billing.payment.collect','billing.claim.manage','platform.subscription.view']),
    ('CLINIC', 'medical_coder', ARRAY[
        'platform.dashboard.view','platform.report.view','clinical.encounter.view','billing.coding.suggest',
        'billing.coding.assign','ai.assistant.use']),
    ('CLINIC', 'compliance_officer', ARRAY[
        'platform.dashboard.view','platform.report.view','compliance.audit.view','compliance.consent.manage',
        'compliance.access.monitor']),
    ('CLINIC', 'assistant', ARRAY[
        'platform.dashboard.view','platform.document.view','patient.record.register','patient.record.view',
        'scheduling.appointment.view','scheduling.appointment.book']),
    ('LAB', 'lab_admin', ARRAY[
        'platform.dashboard.view','platform.document.view','platform.document.create','platform.document.edit',
        'platform.report.view','platform.member.view','platform.member.invite','platform.member.manage',
        'platform.role.manage','platform.settings.manage','platform.branding.manage','platform.navigation.manage',
        'platform.navigation.edit','platform.navigation.publish','platform.subscription.view','platform.subscription.manage',
        'integration.connection.manage','patient.record.register','patient.record.view','lab.order.create',
        'lab.result.enter','lab.result.view','lab.sample.manage','lab.catalog.manage','billing.invoice.create',
        'billing.invoice.view','billing.payment.collect']),
    ('LAB', 'lab_technician', ARRAY[
        'platform.dashboard.view','platform.document.view','patient.record.view','lab.result.enter',
        'lab.result.view','lab.sample.manage']),
    ('LAB', 'phlebotomist', ARRAY[
        'platform.dashboard.view','patient.record.view','scheduling.appointment.view','lab.result.view',
        'lab.sample.manage']),
    ('LAB', 'front_desk', ARRAY[
        'platform.dashboard.view','platform.document.view','patient.record.register','patient.record.view',
        'lab.order.create','scheduling.appointment.view','scheduling.appointment.book',
        'scheduling.appointment.reschedule','billing.invoice.create','billing.invoice.view','billing.payment.collect']),
    ('STORE', 'store_admin', ARRAY[
        'platform.dashboard.view','platform.document.view','platform.document.create','platform.document.edit',
        'platform.report.view','platform.member.view','platform.member.invite','platform.member.manage',
        'platform.role.manage','platform.settings.manage','platform.branding.manage','platform.navigation.manage',
        'platform.navigation.edit','platform.navigation.publish','platform.subscription.view','platform.subscription.manage','compliance.audit.view',
        'patient.record.register','patient.record.view','pharmacy.inventory.manage','billing.invoice.create',
        'billing.invoice.view','billing.payment.collect']),
    ('STORE', 'pharmacist', ARRAY[
        'platform.dashboard.view','platform.document.view','patient.record.view','pharmacy.prescription.dispense',
        'pharmacy.inventory.manage','billing.invoice.create','billing.invoice.view','billing.payment.collect']),
    ('STORE', 'counter_staff', ARRAY[
        'platform.dashboard.view','patient.record.view','billing.invoice.create','billing.invoice.view',
        'billing.payment.collect'])
) AS m(tenant_type, code, privs) ON m.tenant_type = t.tenant_type AND m.code = t.code;

-- ══ 3. Roles ═════════════════════════════════════════════════════════

CREATE TABLE authz_roles (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    -- NULL = platform role (operators).
    tenant_id   UUID        REFERENCES tenants(id) ON DELETE CASCADE,
    template_id UUID        REFERENCES authz_role_templates(id),
    kind        TEXT        NOT NULL CHECK (kind IN ('TEMPLATE','DERIVED','CUSTOM','PLATFORM')),
    code        TEXT        NOT NULL CHECK (code ~ '^[a-z][a-z0-9_]{0,62}$'),
    name        TEXT        NOT NULL,
    description TEXT,
    is_active   BOOLEAN     NOT NULL DEFAULT true,
    created_by  UUID,
    updated_by  UUID,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_authz_roles_shape CHECK (
           (kind IN ('TEMPLATE','DERIVED') AND tenant_id IS NOT NULL AND template_id IS NOT NULL)
        OR (kind = 'CUSTOM'   AND tenant_id IS NOT NULL AND template_id IS NULL)
        OR (kind = 'PLATFORM' AND tenant_id IS NULL     AND template_id IS NULL))
);

CREATE UNIQUE INDEX uq_authz_roles_tenant_code   ON authz_roles(tenant_id, code) WHERE tenant_id IS NOT NULL;
CREATE UNIQUE INDEX uq_authz_roles_platform_code ON authz_roles(code)            WHERE tenant_id IS NULL;
-- One plain copy of each template per tenant.
CREATE UNIQUE INDEX uq_authz_roles_template      ON authz_roles(tenant_id, template_id) WHERE kind = 'TEMPLATE';

CREATE TABLE authz_role_privilege_deltas (
    role_id        UUID        NOT NULL REFERENCES authz_roles(id) ON DELETE CASCADE,
    privilege_code TEXT        NOT NULL REFERENCES authz_privileges(code) ON UPDATE CASCADE,
    effect         TEXT        NOT NULL CHECK (effect IN ('GRANT','DENY')),
    granted_by     UUID,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (role_id, privilege_code)
);

-- ══ 4. Memberships and assignments ═══════════════════════════════════

CREATE TABLE authz_memberships (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    -- NULL = platform membership (operators).
    tenant_id  UUID        REFERENCES tenants(id) ON DELETE CASCADE,
    -- Keycloak user id (token "sub").
    user_sub   UUID        NOT NULL,
    status     TEXT        NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('INVITED','ACTIVE','SUSPENDED')),
    created_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_authz_memberships_tenant   ON authz_memberships(tenant_id, user_sub) WHERE tenant_id IS NOT NULL;
CREATE UNIQUE INDEX uq_authz_memberships_platform ON authz_memberships(user_sub)            WHERE tenant_id IS NULL;
CREATE INDEX        idx_authz_memberships_user    ON authz_memberships(user_sub);

CREATE TABLE authz_role_assignments (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    membership_id UUID        NOT NULL REFERENCES authz_memberships(id) ON DELETE CASCADE,
    role_id       UUID        NOT NULL REFERENCES authz_roles(id),
    is_primary    BOOLEAN     NOT NULL DEFAULT false,
    -- Empty = every location of the tenant.
    location_ids  UUID[]      NOT NULL DEFAULT '{}',
    valid_from    TIMESTAMPTZ NOT NULL DEFAULT now(),
    valid_to      TIMESTAMPTZ,
    assigned_by   UUID,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_authz_role_assignments UNIQUE (membership_id, role_id),
    CONSTRAINT chk_authz_role_assignments_validity CHECK (valid_to IS NULL OR valid_to > valid_from),
    -- The primary role is the member's job: not time-bound.
    CONSTRAINT chk_authz_role_assignments_primary_open CHECK (NOT is_primary OR valid_to IS NULL)
);

CREATE UNIQUE INDEX uq_authz_role_assignments_primary ON authz_role_assignments(membership_id) WHERE is_primary;
CREATE INDEX        idx_authz_role_assignments_role   ON authz_role_assignments(role_id);

-- ══ 5. Integrity the checks above cannot express ═════════════════════
-- The services enforce these too; the triggers stop a bad seed or manual fix.

CREATE FUNCTION authz_check_template_privilege() RETURNS trigger AS $$
DECLARE t_type TEXT;
BEGIN
    SELECT tenant_type INTO t_type FROM authz_role_templates WHERE id = NEW.template_id;
    IF NOT EXISTS (SELECT 1 FROM authz_privileges p
                   WHERE p.code = NEW.privilege_code AND t_type = ANY(p.tenant_types)) THEN
        RAISE EXCEPTION 'Privilege % does not exist for tenant type %', NEW.privilege_code, t_type;
    END IF;
    RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_authz_template_privilege
    BEFORE INSERT OR UPDATE ON authz_role_template_privileges
    FOR EACH ROW EXECUTE FUNCTION authz_check_template_privilege();

CREATE FUNCTION authz_check_role_delta() RETURNS trigger AS $$
DECLARE r_kind TEXT; r_tenant UUID; t_type TEXT;
BEGIN
    SELECT r.kind, r.tenant_id INTO r_kind, r_tenant FROM authz_roles r WHERE r.id = NEW.role_id;
    IF r_kind = 'PLATFORM' THEN
        IF NOT EXISTS (SELECT 1 FROM authz_privileges p
                       WHERE p.code = NEW.privilege_code AND NOT p.assignable_by_tenant) THEN
            RAISE EXCEPTION 'Platform roles hold platform-only privileges, not %', NEW.privilege_code;
        END IF;
        RETURN NEW;
    END IF;
    SELECT COALESCE(type, 'CLINIC') INTO t_type FROM tenants WHERE id = r_tenant;
    IF NOT EXISTS (SELECT 1 FROM authz_privileges p
                   WHERE p.code = NEW.privilege_code AND p.assignable_by_tenant AND t_type = ANY(p.tenant_types)) THEN
        RAISE EXCEPTION 'Privilege % cannot be used in a % tenant role', NEW.privilege_code, t_type;
    END IF;
    RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_authz_role_delta
    BEFORE INSERT OR UPDATE ON authz_role_privilege_deltas
    FOR EACH ROW EXECUTE FUNCTION authz_check_role_delta();

CREATE FUNCTION authz_check_assignment() RETURNS trigger AS $$
DECLARE m_tenant UUID; r_tenant UUID; r_kind TEXT; r_active BOOLEAN;
BEGIN
    SELECT tenant_id INTO m_tenant FROM authz_memberships WHERE id = NEW.membership_id;
    SELECT tenant_id, kind, is_active INTO r_tenant, r_kind, r_active FROM authz_roles WHERE id = NEW.role_id;
    IF m_tenant IS DISTINCT FROM r_tenant THEN
        RAISE EXCEPTION 'Role % belongs to another tenant than membership %', NEW.role_id, NEW.membership_id;
    END IF;
    IF NEW.is_primary AND r_kind NOT IN ('TEMPLATE','DERIVED','PLATFORM') THEN
        RAISE EXCEPTION 'A % role cannot be a primary role', r_kind;
    END IF;
    IF NOT r_active THEN
        RAISE EXCEPTION 'Role % is not active', NEW.role_id;
    END IF;
    RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_authz_assignment
    BEFORE INSERT OR UPDATE ON authz_role_assignments
    FOR EACH ROW EXECUTE FUNCTION authz_check_assignment();

-- ══ 6. Platform roles ════════════════════════════════════════════════

INSERT INTO authz_roles (kind, code, name, description) VALUES
    ('PLATFORM', 'super_admin',         'Super admin',         'All platform operator privileges'),
    ('PLATFORM', 'support',             'Support',             'Views tenants and opens support sessions'),
    ('PLATFORM', 'onboarding_reviewer', 'Onboarding reviewer', 'Reviews and verifies tenants');

INSERT INTO authz_role_privilege_deltas (role_id, privilege_code, effect)
SELECT r.id, unnest(m.privs), 'GRANT'
FROM authz_roles r
JOIN (VALUES
    ('super_admin',         ARRAY['platform.tenant.view','platform.tenant.manage','platform.tenant.verify',
                                  'platform.template.manage','platform.support.session']),
    ('support',             ARRAY['platform.tenant.view','platform.support.session']),
    ('onboarding_reviewer', ARRAY['platform.tenant.view','platform.tenant.verify'])
) AS m(code, privs) ON m.code = r.code
WHERE r.tenant_id IS NULL;

-- ══ 7. Plan features for role editing (enabled on every plan) ════════

INSERT INTO features (code, name, type, category, description) VALUES
    ('authz.derived_roles', 'Derived roles', 'BOOLEAN', 'CORE', 'Create and edit roles derived from templates'),
    ('authz.custom_roles',  'Custom roles',  'BOOLEAN', 'CORE', 'Create and edit roles built from scratch')
ON CONFLICT (code) DO NOTHING;

INSERT INTO plan_features (plan_id, feature_id, enabled)
SELECT p.id, f.id, true
FROM plans p CROSS JOIN features f
WHERE f.code IN ('authz.derived_roles', 'authz.custom_roles')
ON CONFLICT DO NOTHING;

-- ══ 8. Backfill from the old model ═══════════════════════════════════

-- 8.1 Every tenant gets one TEMPLATE role per template of its type (no type = CLINIC).
INSERT INTO authz_roles (tenant_id, template_id, kind, code, name, description)
SELECT t.id, tp.id, 'TEMPLATE', tp.code, tp.name, tp.description
FROM tenants t
JOIN authz_role_templates tp ON tp.tenant_type = COALESCE(t.type, 'CLINIC');

-- 8.2 A membership per tenant user. Patients are not members.
INSERT INTO authz_memberships (tenant_id, user_sub, status)
SELECT u.tenant_id, u.auth_user_id,
       CASE WHEN upper(u.status) = 'ACTIVE' THEN 'ACTIVE' ELSE 'SUSPENDED' END
FROM users u
WHERE lower(u.role) <> 'patient';

-- 8.3 The primary role, mapped from users.role for the tenant's type.
WITH role_map (legacy_role, tenant_type, template_code) AS (
    VALUES
        ('clinic_admin',       'CLINIC', 'clinic_admin'),
        ('doctor',             'CLINIC', 'doctor'),
        ('nurse',              'CLINIC', 'nurse'),
        ('receptionist',       'CLINIC', 'receptionist'),
        ('billing_staff',      'CLINIC', 'billing_staff'),
        ('medical_coder',      'CLINIC', 'medical_coder'),
        ('compliance_officer', 'CLINIC', 'compliance_officer'),
        ('lab_admin',          'LAB',    'lab_admin'),
        ('lab_technician',     'LAB',    'lab_technician'),
        ('phlebotomist',       'LAB',    'phlebotomist'),
        ('receptionist',       'LAB',    'front_desk'),
        ('pharmacist',         'STORE',  'pharmacist'),
        ('receptionist',       'STORE',  'counter_staff')
), chosen AS (
    SELECT m.id AS membership_id,
           COALESCE(
               -- Tenant admins and platform admins run their tenant.
               CASE WHEN lower(u.role) IN ('tenantadmin', 'superadmin')
                    THEN (SELECT id FROM authz_role_templates WHERE tenant_type = COALESCE(t.type, 'CLINIC') AND is_admin) END,
               (SELECT tp.id FROM role_map rm
                  JOIN authz_role_templates tp ON tp.tenant_type = rm.tenant_type AND tp.code = rm.template_code
                 WHERE rm.legacy_role = lower(u.role) AND rm.tenant_type = COALESCE(t.type, 'CLINIC')),
               (SELECT id FROM authz_role_templates WHERE tenant_type = COALESCE(t.type, 'CLINIC') AND is_fallback)
           ) AS template_id,
           u.tenant_id
    FROM authz_memberships m
    JOIN users u   ON u.tenant_id = m.tenant_id AND u.auth_user_id = m.user_sub
    JOIN tenants t ON t.id = m.tenant_id
)
INSERT INTO authz_role_assignments (membership_id, role_id, is_primary)
SELECT c.membership_id, r.id, true
FROM chosen c
JOIN authz_roles r ON r.tenant_id = c.tenant_id AND r.template_id = c.template_id AND r.kind = 'TEMPLATE';

-- 8.4 Platform super admins also get a platform membership.
INSERT INTO authz_memberships (tenant_id, user_sub, status)
SELECT DISTINCT NULL::UUID, u.auth_user_id, 'ACTIVE'
FROM users u
WHERE lower(u.role) = 'superadmin'
ON CONFLICT DO NOTHING;

INSERT INTO authz_role_assignments (membership_id, role_id, is_primary)
SELECT m.id, r.id, true
FROM authz_memberships m
JOIN authz_roles r ON r.tenant_id IS NULL AND r.code = 'super_admin'
WHERE m.tenant_id IS NULL;
