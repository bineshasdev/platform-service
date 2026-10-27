-- ============================================================
-- V21 — Tenant user groups for menu targeting (menu stage 3)
--
--   user_groups        a tenant's own groups, scoped to one application
--   user_group_members flat membership (no nesting, not time-bound)
--   menu_group_access  GRANT / DENY of a menu item to a group
--
-- Groups belong to a tenant, so two tenants can each have a "reception"
-- group with different menu access. A group grants navigation only: it
-- never grants a privilege, and plan / feature / privilege gates still apply.
-- ============================================================

CREATE TABLE user_groups (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID        NOT NULL REFERENCES tenants(id)      ON DELETE CASCADE,
    application_id UUID        NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    code           TEXT        NOT NULL,
    name           TEXT        NOT NULL,
    description    TEXT,
    is_active      BOOLEAN     NOT NULL DEFAULT true,
    created_by     UUID,
    updated_by     UUID,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_groups_code UNIQUE (tenant_id, application_id, code)
);

CREATE TABLE user_group_members (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id   UUID        NOT NULL REFERENCES user_groups(id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES users(id)       ON DELETE CASCADE,
    added_by   UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_group_member UNIQUE (group_id, user_id)
);

CREATE INDEX idx_user_group_members_user ON user_group_members(user_id);

CREATE TABLE menu_group_access (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    menu_item_id UUID        NOT NULL REFERENCES menu_items(id) ON DELETE CASCADE,
    group_id     UUID        NOT NULL REFERENCES user_groups(id) ON DELETE CASCADE,
    -- A DENY beats every GRANT, including a user-level one.
    effect       TEXT        NOT NULL CHECK (effect IN ('GRANT', 'DENY')),
    reason       TEXT,
    assigned_by  UUID,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_menu_group_access UNIQUE (menu_item_id, group_id)
);

CREATE INDEX idx_menu_group_access_group ON menu_group_access(group_id);

INSERT INTO privileges (code, name, category, module_code, is_phi) VALUES
    ('group.manage', 'Manage user groups and membership', 'ADMIN', 'M01', false);

-- Whoever manages users manages groups.
INSERT INTO role_privileges (role_id, privilege_id)
SELECT rp.role_id, np.id
FROM role_privileges rp
JOIN privileges up ON up.id = rp.privilege_id AND up.code = 'user.manage'
CROSS JOIN privileges np
WHERE np.code = 'group.manage'
ON CONFLICT DO NOTHING;
