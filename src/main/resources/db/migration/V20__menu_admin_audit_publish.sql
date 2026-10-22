-- ============================================================
-- V20 — Menu administration (menu stage 2)
--
--   audit_log           general, append-only audit trail (reused by every module)
--   menu_items.tenant_id  NULL = platform-global item; set = tenant-owned item
--   menu_change_sets    the open DRAFT of a scope: an ordered list of operations
--   menu_snapshots      immutable published versions of a scope (rollback source)
--   menu.manage / menu.publish privileges
--
-- A "scope" is (application, tenant): tenant NULL is the global menu that
-- platform operators publish; a tenant scope holds that tenant's own items,
-- overrides and assignments and never alters the global menu.
-- ============================================================

-- ══ 1. General audit log ══════════════════════════════════════════════
CREATE TABLE audit_log (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    occurred_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Deliberately no FK: audit rows must outlive the tenant they describe.
    tenant_id      UUID,
    actor_id       UUID,
    actor_type     TEXT        NOT NULL DEFAULT 'USER'
                       CHECK (actor_type IN ('USER', 'SERVICE', 'SYSTEM')),
    action         TEXT        NOT NULL,
    entity_type    TEXT        NOT NULL,
    entity_id      TEXT,
    before_state   JSONB,
    after_state    JSONB,
    reason         TEXT,
    correlation_id TEXT,
    source         TEXT        NOT NULL DEFAULT 'API',
    metadata       JSONB       NOT NULL DEFAULT '{}'
);

CREATE INDEX idx_audit_log_tenant_time ON audit_log(tenant_id, occurred_at DESC);
CREATE INDEX idx_audit_log_entity      ON audit_log(entity_type, entity_id);
CREATE INDEX idx_audit_log_action      ON audit_log(action, occurred_at DESC);
CREATE INDEX idx_audit_log_correlation ON audit_log(correlation_id);

CREATE FUNCTION audit_log_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_log is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_log_immutable
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_immutable();

COMMENT ON TABLE audit_log IS 'Append-only. UPDATE and DELETE are rejected by trigger.';

-- ══ 2. Tenant-owned menu items ════════════════════════════════════════
ALTER TABLE menu_items
    ADD COLUMN tenant_id UUID REFERENCES tenants(id) ON DELETE CASCADE;

CREATE INDEX idx_menu_items_tenant ON menu_items(tenant_id) WHERE tenant_id IS NOT NULL;

-- A code is unique within (application, owner). Application code additionally
-- refuses a tenant code that equals a global one, so a merged scope view is unambiguous.
ALTER TABLE menu_items DROP CONSTRAINT uq_menu_items_app_code;
CREATE UNIQUE INDEX uq_menu_items_scope_code
    ON menu_items (application_id,
                   COALESCE(tenant_id, '00000000-0000-0000-0000-000000000000'::uuid),
                   code);

-- ══ 3. Drafts (change-sets) ═══════════════════════════════════════════
CREATE TABLE menu_change_sets (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id UUID        NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    tenant_id      UUID        REFERENCES tenants(id) ON DELETE CASCADE,
    status         TEXT        NOT NULL DEFAULT 'OPEN'
                       CHECK (status IN ('OPEN', 'PUBLISHED', 'DISCARDED')),
    title          TEXT,
    -- Ordered operations; replayed on preview and publish.
    ops            JSONB       NOT NULL DEFAULT '[]',
    -- Scope version the draft was started from; publish refuses a stale base.
    base_version   INT         NOT NULL,
    row_version    BIGINT      NOT NULL DEFAULT 0,
    created_by     UUID,
    updated_by     UUID,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_by   UUID,
    published_at   TIMESTAMPTZ,
    snapshot_id    UUID
);

-- At most one open draft per scope.
CREATE UNIQUE INDEX uq_menu_change_sets_open
    ON menu_change_sets (application_id,
                         COALESCE(tenant_id, '00000000-0000-0000-0000-000000000000'::uuid))
    WHERE status = 'OPEN';

-- ══ 4. Published snapshots ════════════════════════════════════════════
CREATE TABLE menu_snapshots (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id UUID        NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    tenant_id      UUID        REFERENCES tenants(id) ON DELETE CASCADE,
    version        INT         NOT NULL,
    -- Canonical scope state (see MenuScopeStateReader). Version 0 is the baseline.
    state          JSONB       NOT NULL,
    change_set_id  UUID,
    note           TEXT,
    published_by   UUID,
    published_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_menu_snapshots_version
    ON menu_snapshots (application_id,
                       COALESCE(tenant_id, '00000000-0000-0000-0000-000000000000'::uuid),
                       version);

-- ══ 5. Privileges ═════════════════════════════════════════════════════
INSERT INTO privileges (code, name, category, module_code, is_phi) VALUES
    ('menu.manage',  'Edit menu drafts',        'ADMIN', 'M01', false),
    ('menu.publish', 'Publish and roll back menus', 'ADMIN', 'M01', false);

-- Whoever could assign menus keeps working: they get manage + publish too.
INSERT INTO role_privileges (role_id, privilege_id)
SELECT rp.role_id, np.id
FROM role_privileges rp
JOIN privileges ap ON ap.id = rp.privilege_id AND ap.code = 'menu.assign'
CROSS JOIN privileges np
WHERE np.code IN ('menu.manage', 'menu.publish')
ON CONFLICT DO NOTHING;
