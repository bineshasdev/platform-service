-- =============================================================================
-- V5 — Seed Data
-- Plans, features, plan-feature mapping, menus, settings definitions,
-- app settings defaults, and lookup reference data.
-- All inserts are idempotent via ON CONFLICT DO NOTHING.
-- =============================================================================

-- =============================================================================
-- Plans
-- =============================================================================
INSERT INTO plans (code, name, billing_cycle, price) VALUES
('free',       'Free',       NULL,      0.00),
('starter',    'Starter',    'monthly', 29.00),
('pro',        'Pro',        'monthly', 79.00),
('enterprise', 'Enterprise', 'monthly', 199.00)
ON CONFLICT (code) DO NOTHING;

-- =============================================================================
-- Features
-- =============================================================================
INSERT INTO features (code, name, type, category, description) VALUES
('doc_upload',        'Document Upload',      'USAGE_LIMITED', 'CORE',        'Upload and store documents'),
('doc_search',        'Document Search',      'BOOLEAN',       'CORE',        'Full-text search across documents'),
('semantic_search',   'AI Semantic Search',   'BOOLEAN',       'AI',          'Vector-based semantic search'),
('ocr',               'OCR Processing',       'USAGE_LIMITED', 'AI',          'Optical character recognition'),
('ai_classification', 'AI Classification',    'USAGE_LIMITED', 'AI',          'Auto-classify document types'),
('ai_extraction',     'AI Data Extraction',   'USAGE_LIMITED', 'AI',          'Extract structured data from documents'),
('workflows',         'Workflow Automation',  'BOOLEAN',       'WORKFLOW',    'Create custom approval workflows'),
('custom_branding',   'Custom Branding',      'BOOLEAN',       'BRANDING',    'Custom logo, theme, and colors'),
('api_access',        'API Access',           'BOOLEAN',       'INTEGRATION', 'REST API access for integrations'),
('webhooks',          'Webhooks',             'USAGE_LIMITED', 'INTEGRATION', 'Outbound webhook notifications'),
('erp_integration',   'ERP Integration',      'BOOLEAN',       'INTEGRATION', 'Connect to ERP systems'),
('storage',           'Storage',              'USAGE_LIMITED', 'STORAGE',     'File storage capacity in MB'),
('users',             'Team Members',         'USAGE_LIMITED', 'CORE',        'Number of user seats')
ON CONFLICT (code) DO NOTHING;

-- =============================================================================
-- Plan ↔ Feature mapping
-- =============================================================================

-- Free plan
INSERT INTO plan_features (plan_id, feature_id, enabled, usage_limit, reset_period)
SELECT p.id, f.id,
    CASE f.code
        WHEN 'doc_upload'  THEN true
        WHEN 'doc_search'  THEN true
        WHEN 'storage'     THEN true
        WHEN 'users'       THEN true
        WHEN 'workflows'   THEN true
        ELSE false
    END,
    CASE f.code
        WHEN 'doc_upload' THEN 50
        WHEN 'storage'    THEN 500
        WHEN 'users'      THEN 2
        ELSE NULL
    END,
    CASE f.type WHEN 'USAGE_LIMITED' THEN 'MONTHLY' ELSE 'NONE' END
FROM plans p, features f
WHERE p.code = 'free'
ON CONFLICT (plan_id, feature_id) DO NOTHING;

-- Starter plan
INSERT INTO plan_features (plan_id, feature_id, enabled, usage_limit, reset_period)
SELECT p.id, f.id,
    CASE f.code WHEN 'erp_integration' THEN false ELSE true END,
    CASE f.code
        WHEN 'doc_upload'        THEN 500
        WHEN 'ocr'               THEN 200
        WHEN 'ai_classification' THEN 200
        WHEN 'ai_extraction'     THEN 100
        WHEN 'webhooks'          THEN 10
        WHEN 'storage'           THEN 5000
        WHEN 'users'             THEN 5
        ELSE NULL
    END,
    CASE f.type WHEN 'USAGE_LIMITED' THEN 'MONTHLY' ELSE 'NONE' END
FROM plans p, features f
WHERE p.code = 'starter'
ON CONFLICT (plan_id, feature_id) DO NOTHING;

-- Pro plan
INSERT INTO plan_features (plan_id, feature_id, enabled, usage_limit, reset_period)
SELECT p.id, f.id,
    true,
    CASE f.code
        WHEN 'doc_upload'        THEN 5000
        WHEN 'ocr'               THEN 2000
        WHEN 'ai_classification' THEN 2000
        WHEN 'ai_extraction'     THEN 1000
        WHEN 'webhooks'          THEN 50
        WHEN 'storage'           THEN 50000
        WHEN 'users'             THEN 25
        ELSE NULL
    END,
    CASE f.type WHEN 'USAGE_LIMITED' THEN 'MONTHLY' ELSE 'NONE' END
FROM plans p, features f
WHERE p.code = 'pro'
ON CONFLICT (plan_id, feature_id) DO NOTHING;

-- Enterprise plan (unlimited)
INSERT INTO plan_features (plan_id, feature_id, enabled, usage_limit, reset_period)
SELECT p.id, f.id, true, NULL, 'NONE'
FROM plans p, features f
WHERE p.code = 'enterprise'
ON CONFLICT (plan_id, feature_id) DO NOTHING;

-- =============================================================================
-- Menu items
-- =============================================================================

-- Top-level
INSERT INTO menu_items (code, label, icon, route, type, sort_order) VALUES
('dashboard',    'Dashboard',      'LayoutDashboard', '/dashboard',    'LINK',  1),
('documents',    'Documents',      'FileText',        NULL,            'GROUP', 2),
('ai_studio',    'AI Studio',      'Brain',           NULL,            'GROUP', 3),
('workflows',    'Workflows',      'GitBranch',       NULL,            'GROUP', 4),
('reports',      'Reports',        'BarChart3',       '/reports',      'LINK',  5),
('integrations', 'Integrations',   'Plug',            '/integrations', 'LINK',  6),
('admin',        'Administration', 'Settings',        NULL,            'GROUP', 7)
ON CONFLICT (code) DO NOTHING;

-- Documents children
INSERT INTO menu_items (parent_id, code, label, icon, route, type, sort_order)
SELECT id, 'doc_upload',    'Upload',        'Upload',   '/documents/upload',    'LINK', 1 FROM menu_items WHERE code = 'documents'
UNION ALL
SELECT id, 'doc_list',      'All Documents', 'Files',    '/documents',           'LINK', 2 FROM menu_items WHERE code = 'documents'
UNION ALL
SELECT id, 'doc_templates', 'Templates',     'FileCode', '/documents/templates', 'LINK', 3 FROM menu_items WHERE code = 'documents'
ON CONFLICT (code) DO NOTHING;

-- AI Studio children
INSERT INTO menu_items (parent_id, code, label, icon, route, type, sort_order, required_feature)
SELECT id, 'ai_processing', 'Processing Queue', 'Cpu',   '/ai/queue',  'LINK', 1, 'ocr'               FROM menu_items WHERE code = 'ai_studio'
UNION ALL
SELECT id, 'ai_models',     'Models',           'Boxes', '/ai/models', 'LINK', 2, 'ai_classification' FROM menu_items WHERE code = 'ai_studio'
ON CONFLICT (code) DO NOTHING;

-- Workflows children
INSERT INTO menu_items (parent_id, code, label, icon, route, type, sort_order)
SELECT id, 'wf_builder',    'Workflow Builder',  'Workflow',    '/workflows',         'LINK', 1 FROM menu_items WHERE code = 'workflows'
UNION ALL
SELECT id, 'wf_forms',      'Form Builder',      'Layers',      '/forms',             'LINK', 2 FROM menu_items WHERE code = 'workflows'
UNION ALL
SELECT id, 'wf_instances',  'Active Workflows',  'Play',        '/workflows/active',  'LINK', 3 FROM menu_items WHERE code = 'workflows'
UNION ALL
SELECT id, 'wf_approvals',  'My Approvals',      'CheckSquare', '/workflows/approvals','LINK', 4 FROM menu_items WHERE code = 'workflows'
ON CONFLICT (code) DO NOTHING;

-- Admin children
INSERT INTO menu_items (parent_id, code, label, icon, route, type, sort_order)
SELECT id, 'admin_users',    'Users',    'Users',      '/admin/users',    'LINK', 1 FROM menu_items WHERE code = 'admin'
UNION ALL
SELECT id, 'admin_billing',  'Billing',  'CreditCard', '/admin/billing',  'LINK', 2 FROM menu_items WHERE code = 'admin'
UNION ALL
SELECT id, 'admin_settings', 'Settings', 'Sliders',    '/admin/settings', 'LINK', 3 FROM menu_items WHERE code = 'admin'
UNION ALL
SELECT id, 'admin_branding', 'Branding', 'Palette',    '/admin/branding', 'LINK', 4 FROM menu_items WHERE code = 'admin'
ON CONFLICT (code) DO NOTHING;

-- Role access — all roles: dashboard, documents, reports, approvals
INSERT INTO menu_role_access (menu_item_id, role)
SELECT id, r.role FROM menu_items, (VALUES ('OWNER'),('ADMIN'),('MANAGER'),('USER'),('VIEWER')) AS r(role)
WHERE code IN ('dashboard','documents','doc_upload','doc_list','doc_templates','reports','wf_approvals')
ON CONFLICT (menu_item_id, role) DO NOTHING;

-- AI Studio, Workflows, Integrations — OWNER, ADMIN, MANAGER
INSERT INTO menu_role_access (menu_item_id, role)
SELECT id, r.role FROM menu_items, (VALUES ('OWNER'),('ADMIN'),('MANAGER')) AS r(role)
WHERE code IN ('ai_studio','ai_processing','ai_models','workflows','wf_builder','wf_forms','wf_instances','integrations')
ON CONFLICT (menu_item_id, role) DO NOTHING;

-- Admin — OWNER, ADMIN only
INSERT INTO menu_role_access (menu_item_id, role)
SELECT id, r.role FROM menu_items, (VALUES ('OWNER'),('ADMIN')) AS r(role)
WHERE code IN ('admin','admin_users','admin_billing','admin_settings','admin_branding')
ON CONFLICT (menu_item_id, role) DO NOTHING;

-- Plan access — all menus on all plans
INSERT INTO menu_plan_access (menu_item_id, plan)
SELECT id, p.plan FROM menu_items, (VALUES ('free'),('starter'),('pro'),('enterprise')) AS p(plan)
ON CONFLICT (menu_item_id, plan) DO NOTHING;

-- =============================================================================
-- Setting definitions
-- =============================================================================
INSERT INTO setting_definitions (key, category, data_type, default_value, tenant_overridable, user_overridable, scope, description) VALUES
('theme.mode',              'APPEARANCE',    'ENUM',    'light',      true,  true,  'ALL',    'Color theme mode'),
('theme.primaryColor',      'APPEARANCE',    'STRING',  '#3ecf8e',    true,  false, 'TENANT', 'Primary brand color'),
('locale',                  'LOCALE',        'STRING',  'en-US',      true,  true,  'ALL',    'Language/locale'),
('timezone',                'LOCALE',        'STRING',  'UTC',        true,  true,  'ALL',    'Timezone'),
('date_format',             'LOCALE',        'STRING',  'YYYY-MM-DD', true,  true,  'ALL',    'Date display format'),
('ai.autoClassify',         'AI',            'BOOLEAN', 'true',       true,  false, 'TENANT', 'Auto-classify uploaded documents'),
('ai.autoExtract',          'AI',            'BOOLEAN', 'false',      true,  false, 'TENANT', 'Auto-extract data from documents'),
('notifications.email',     'NOTIFICATIONS', 'BOOLEAN', 'true',       true,  true,  'ALL',    'Email notifications'),
('notifications.push',      'NOTIFICATIONS', 'BOOLEAN', 'true',       true,  true,  'ALL',    'Push notifications'),
('notifications.inApp',     'NOTIFICATIONS', 'BOOLEAN', 'true',       false, true,  'USER',   'In-app notifications'),
('security.mfaRequired',    'SECURITY',      'BOOLEAN', 'false',      true,  false, 'TENANT', 'Require MFA for all users'),
('security.sessionTimeout', 'SECURITY',      'INTEGER', '3600',       true,  false, 'TENANT', 'Session timeout in seconds')
ON CONFLICT (key) DO NOTHING;

-- App-level defaults
INSERT INTO app_settings (key, value)
SELECT key, default_value FROM setting_definitions WHERE default_value IS NOT NULL
ON CONFLICT (key) DO NOTHING;

-- =============================================================================
-- Lookup reference data
-- =============================================================================
INSERT INTO lookup_categories (code, name) VALUES
('countries',      'Countries'),
('states',         'States/Provinces'),
('currencies',     'Currencies'),
('timezones',      'Timezones'),
('document_types', 'Document Types')
ON CONFLICT (code) DO NOTHING;

-- Countries
INSERT INTO lookup_values (category_id, code, label, sort_order, metadata)
SELECT c.id, v.code, v.label, v.sort_order, v.metadata::jsonb
FROM lookup_categories c,
(VALUES
    ('IN', 'India',          1,  '{"dialCode":"+91","flag":"🇮🇳"}'),
    ('US', 'United States',  2,  '{"dialCode":"+1","flag":"🇺🇸"}'),
    ('GB', 'United Kingdom', 3,  '{"dialCode":"+44","flag":"🇬🇧"}'),
    ('CA', 'Canada',         4,  '{"dialCode":"+1","flag":"🇨🇦"}'),
    ('AU', 'Australia',      5,  '{"dialCode":"+61","flag":"🇦🇺"}'),
    ('DE', 'Germany',        6,  '{"dialCode":"+49","flag":"🇩🇪"}'),
    ('FR', 'France',         7,  '{"dialCode":"+33","flag":"🇫🇷"}'),
    ('SG', 'Singapore',      8,  '{"dialCode":"+65","flag":"🇸🇬"}'),
    ('JP', 'Japan',          9,  '{"dialCode":"+81","flag":"🇯🇵"}'),
    ('AE', 'UAE',            10, '{"dialCode":"+971","flag":"🇦🇪"}')
) AS v(code, label, sort_order, metadata)
WHERE c.code = 'countries'
ON CONFLICT (category_id, code) DO NOTHING;

-- Indian states
INSERT INTO lookup_values (category_id, code, label, parent_code, sort_order)
SELECT c.id, v.code, v.label, 'IN', v.sort_order
FROM lookup_categories c,
(VALUES
    ('KA', 'Karnataka',      1),
    ('MH', 'Maharashtra',    2),
    ('TN', 'Tamil Nadu',     3),
    ('KL', 'Kerala',         4),
    ('DL', 'Delhi',          5),
    ('UP', 'Uttar Pradesh',  6),
    ('GJ', 'Gujarat',        7),
    ('RJ', 'Rajasthan',      8),
    ('WB', 'West Bengal',    9),
    ('TS', 'Telangana',     10)
) AS v(code, label, sort_order)
WHERE c.code = 'states'
ON CONFLICT (category_id, code) DO NOTHING;

-- Document types
INSERT INTO lookup_values (category_id, code, label, sort_order)
SELECT c.id, v.code, v.label, v.sort_order
FROM lookup_categories c,
(VALUES
    ('invoice',         'Invoice',         1),
    ('receipt',         'Receipt',         2),
    ('contract',        'Contract',        3),
    ('purchase_order',  'Purchase Order',  4),
    ('bank_statement',  'Bank Statement',  5),
    ('tax_form',        'Tax Form',        6),
    ('id_document',     'ID Document',     7),
    ('medical_record',  'Medical Record',  8),
    ('insurance_claim', 'Insurance Claim', 9),
    ('other',           'Other',          99)
) AS v(code, label, sort_order)
WHERE c.code = 'document_types'
ON CONFLICT (category_id, code) DO NOTHING;

-- Currencies
INSERT INTO lookup_values (category_id, code, label, sort_order, metadata)
SELECT c.id, v.code, v.label, v.sort_order, v.metadata::jsonb
FROM lookup_categories c,
(VALUES
    ('INR', 'Indian Rupee',      1, '{"symbol":"₹"}'),
    ('USD', 'US Dollar',         2, '{"symbol":"$"}'),
    ('EUR', 'Euro',              3, '{"symbol":"€"}'),
    ('GBP', 'British Pound',     4, '{"symbol":"£"}'),
    ('AUD', 'Australian Dollar', 5, '{"symbol":"A$"}'),
    ('CAD', 'Canadian Dollar',   6, '{"symbol":"C$"}'),
    ('SGD', 'Singapore Dollar',  7, '{"symbol":"S$"}'),
    ('JPY', 'Japanese Yen',      8, '{"symbol":"¥"}')
) AS v(code, label, sort_order, metadata)
WHERE c.code = 'currencies'
ON CONFLICT (category_id, code) DO NOTHING;
