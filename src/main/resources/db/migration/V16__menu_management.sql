-- ============================================================
-- V16 — Menu management
--
--   menu_items             master catalogue, now scoped per application
--   menu_item_translations localised labels (regional-language requirement)
--   menu_role_access       default menu per role  (role_id FK, was a role enum)
--   menu_plan_access       plan gating (unchanged, from V1)
--   menu_user_access       per-user GRANT / DENY overrides
--   menu_tenant_overrides  tenant relabel / reorder / hide of master items
--
-- Effective menu for (user, application, tenant) is resolved as:
--   1. menu_items WHERE application_id = ? AND is_active AND deleted_at IS NULL
--   2. AND plan gate      — menu_plan_access contains the tenant's plan
--   3. AND feature gate   — required_feature is NULL or enabled for the plan
--   4. AND privilege gate — required_privilege_id is NULL or held by the user
--   5. AND (granted by any of the user's roles OR user-level GRANT)
--   6. AND NOT user-level DENY            <- deny always wins
--   7. THEN apply tenant overrides (hide / relabel / reorder)
-- ============================================================

-- ══ 1. menu_items — application scope + industry-standard columns ═════
ALTER TABLE menu_items
    ADD COLUMN application_id        UUID,
    ADD COLUMN required_privilege_id UUID REFERENCES privileges(id) ON DELETE SET NULL,
    ADD COLUMN description           TEXT,
    ADD COLUMN target                TEXT        NOT NULL DEFAULT 'SELF'
                                         CHECK (target IN ('SELF', 'BLANK')),
    ADD COLUMN depth                 INT         NOT NULL DEFAULT 0,
    ADD COLUMN path                  TEXT,
    -- is_active disables an item outright; is_visible keeps the route reachable
    -- but hides it from navigation (deep links, redirect targets).
    ADD COLUMN is_visible            BOOLEAN     NOT NULL DEFAULT true,
    ADD COLUMN metadata              JSONB       NOT NULL DEFAULT '{}',
    ADD COLUMN updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN created_by            UUID,
    ADD COLUMN updated_by            UUID,
    ADD COLUMN deleted_at            TIMESTAMPTZ;

-- Every pre-existing menu item belongs to the provider console.
UPDATE menu_items
   SET application_id = (SELECT id FROM applications WHERE code = 'PROVIDER_CONSOLE');

ALTER TABLE menu_items
    ALTER COLUMN application_id SET NOT NULL,
    ADD CONSTRAINT fk_menu_items_application
        FOREIGN KEY (application_id) REFERENCES applications(id) ON DELETE CASCADE;

-- `code` is unique per application, not globally: PROVIDER_CONSOLE and
-- PATIENT_PORTAL may each have a 'dashboard'.
ALTER TABLE menu_items DROP CONSTRAINT IF EXISTS menu_items_code_key;
ALTER TABLE menu_items
    ADD CONSTRAINT uq_menu_items_app_code UNIQUE (application_id, code);

-- Materialised path + depth, so the tree is not limited to one nesting level.
WITH RECURSIVE tree AS (
    SELECT id, code, 0 AS depth, code AS path
      FROM menu_items
     WHERE parent_id IS NULL
    UNION ALL
    SELECT c.id, c.code, t.depth + 1, t.path || '/' || c.code
      FROM menu_items c
      JOIN tree t ON c.parent_id = t.id
)
UPDATE menu_items m
   SET depth = t.depth,
       path  = t.path
  FROM tree t
 WHERE m.id = t.id;

CREATE INDEX idx_menu_items_application ON menu_items(application_id);
CREATE INDEX idx_menu_items_parent      ON menu_items(parent_id);
CREATE INDEX idx_menu_items_privilege   ON menu_items(required_privilege_id);
CREATE INDEX idx_menu_items_path        ON menu_items(path text_pattern_ops);
CREATE INDEX idx_menu_items_live        ON menu_items(application_id, is_active)
                                         WHERE deleted_at IS NULL;

-- Wire the existing catalogue to the privileges seeded in V15.
UPDATE menu_items m
   SET required_privilege_id = p.id
  FROM privileges p
 WHERE p.code = CASE m.code
        WHEN 'dashboard'      THEN 'dashboard.view'
        WHEN 'documents'      THEN 'document.view'
        WHEN 'doc_list'       THEN 'document.view'
        WHEN 'doc_templates'  THEN 'document.view'
        WHEN 'doc_upload'     THEN 'document.create'
        WHEN 'reports'        THEN 'report.view'
        WHEN 'wf_approvals'   THEN 'workflow.approve'
        WHEN 'workflows'      THEN 'workflow.view'
        WHEN 'wf_instances'   THEN 'workflow.view'
        WHEN 'wf_builder'     THEN 'workflow.manage'
        WHEN 'wf_forms'       THEN 'workflow.manage'
        WHEN 'ai_studio'      THEN 'ai.use'
        WHEN 'ai_processing'  THEN 'ai.use'
        WHEN 'ai_models'      THEN 'ai.manage'
        WHEN 'integrations'   THEN 'integration.manage'
        WHEN 'admin'          THEN 'settings.manage'
        WHEN 'admin_users'    THEN 'user.manage'
        WHEN 'admin_billing'  THEN 'billing.view'
        WHEN 'admin_settings' THEN 'settings.manage'
        WHEN 'admin_branding' THEN 'branding.manage'
       END;

-- ══ 2. Localised labels ═══════════════════════════════════════════════
CREATE TABLE menu_item_translations (
    menu_item_id UUID        NOT NULL REFERENCES menu_items(id) ON DELETE CASCADE,
    locale       TEXT        NOT NULL,
    label        TEXT        NOT NULL,
    description  TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (menu_item_id, locale)
);

COMMENT ON TABLE menu_item_translations IS
    'Per-locale labels. menu_items.label is the default (en) fallback.';

-- ══ 3. menu_role_access — enum role → roles FK ════════════════════════
ALTER TABLE menu_role_access ADD COLUMN role_id UUID;

UPDATE menu_role_access mra
   SET role_id = r.id
  FROM roles r
 WHERE r.tenant_id IS NULL
   AND r.code = mra.role;

-- Any row whose role text has no matching system role is stale by definition.
DELETE FROM menu_role_access WHERE role_id IS NULL;

ALTER TABLE menu_role_access DROP CONSTRAINT menu_role_access_pkey;
ALTER TABLE menu_role_access DROP CONSTRAINT IF EXISTS menu_role_access_role_check;
ALTER TABLE menu_role_access DROP COLUMN role;

ALTER TABLE menu_role_access
    ALTER COLUMN role_id SET NOT NULL,
    ADD CONSTRAINT menu_role_access_pkey PRIMARY KEY (menu_item_id, role_id),
    ADD CONSTRAINT fk_menu_role_access_role
        FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE;

ALTER TABLE menu_role_access
    ADD COLUMN assigned_by UUID,
    ADD COLUMN created_at  TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE INDEX idx_menu_role_access_role ON menu_role_access(role_id);

-- ══ 4. Per-user overrides ═════════════════════════════════════════════
CREATE TABLE menu_user_access (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    menu_item_id UUID        NOT NULL REFERENCES menu_items(id) ON DELETE CASCADE,
    user_id      UUID        NOT NULL REFERENCES users(id)      ON DELETE CASCADE,
    -- DENY beats every GRANT, including those inherited from a role.
    effect       TEXT        NOT NULL CHECK (effect IN ('GRANT', 'DENY')),
    reason       TEXT,
    valid_from   TIMESTAMPTZ NOT NULL DEFAULT now(),
    valid_to     TIMESTAMPTZ,
    assigned_by  UUID,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_menu_user_access      UNIQUE (menu_item_id, user_id),
    CONSTRAINT chk_menu_user_validity   CHECK (valid_to IS NULL OR valid_to > valid_from)
);

CREATE INDEX idx_menu_user_access_user ON menu_user_access(user_id);

COMMENT ON TABLE menu_user_access IS
    'Per-user additions and revocations layered on top of the role defaults.';

-- ══ 5. Per-tenant overrides of the platform master ════════════════════
CREATE TABLE menu_tenant_overrides (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID        NOT NULL REFERENCES tenants(id)    ON DELETE CASCADE,
    menu_item_id UUID        NOT NULL REFERENCES menu_items(id) ON DELETE CASCADE,
    -- NULL means "inherit the master value".
    label        TEXT,
    icon         TEXT,
    sort_order   INT,
    is_hidden    BOOLEAN     NOT NULL DEFAULT false,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by   UUID,
    updated_by   UUID,
    CONSTRAINT uq_menu_tenant_override UNIQUE (tenant_id, menu_item_id)
);

CREATE INDEX idx_menu_tenant_overrides_tenant ON menu_tenant_overrides(tenant_id);

COMMENT ON TABLE menu_tenant_overrides IS
    'Tenants relabel, reorder and hide master items but cannot create them.';

-- ══ 6. Patient portal starter menu ════════════════════════════════════
INSERT INTO menu_items (application_id, code, label, icon, route, type, sort_order, depth, path,
                        required_privilege_id)
SELECT a.id, v.code, v.label, v.icon, v.route, 'LINK', v.sort_order, 0, v.code, p.id
FROM applications a
CROSS JOIN (VALUES
        ('pp_home',         'Home',            'Home',       '/',              1, 'dashboard.view'),
        ('pp_appointments', 'My Appointments', 'Calendar',   '/appointments',  2, 'appointment.view'),
        ('pp_records',      'My Records',      'FileText',   '/records',       3, 'lab.result.view'),
        ('pp_prescriptions','My Prescriptions','Pill',       '/prescriptions', 4, 'lab.result.view'),
        ('pp_invoices',     'Payments',        'CreditCard', '/payments',      5, 'invoice.view'),
        ('pp_consent',      'Privacy & Consent','ShieldCheck','/consent',      6, 'patient.export')
     ) AS v(code, label, icon, route, sort_order, privilege_code)
JOIN privileges p ON p.code = v.privilege_code
WHERE a.code = 'PATIENT_PORTAL'
ON CONFLICT (application_id, code) DO NOTHING;

-- Patient portal menus are available on every plan.
INSERT INTO menu_plan_access (menu_item_id, plan)
SELECT m.id, pl.plan
FROM menu_items m
JOIN applications a ON a.id = m.application_id AND a.code = 'PATIENT_PORTAL'
CROSS JOIN (VALUES ('free'), ('starter'), ('pro'), ('enterprise')) AS pl(plan)
ON CONFLICT (menu_item_id, plan) DO NOTHING;

-- ...and default to the PATIENT role.
INSERT INTO menu_role_access (menu_item_id, role_id)
SELECT m.id, r.id
FROM menu_items m
JOIN applications a ON a.id = m.application_id AND a.code = 'PATIENT_PORTAL'
JOIN roles r ON r.tenant_id IS NULL AND r.code = 'PATIENT'
ON CONFLICT (menu_item_id, role_id) DO NOTHING;
