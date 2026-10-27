-- ============================================================
-- V23 — Menu catalogue versioning (menu stage 5)
--
-- One counter per (application, tenant) scope; tenant NULL is the global
-- menu. Every instance caches the compiled catalogue keyed by these
-- counters, so a changed counter is the only invalidation signal needed.
--
-- Triggers bump the counter on ANY write to a table that feeds the
-- catalogue — admin publish, temporary menus, the scheduler, module sync,
-- or a hand-run SQL fix — so no code path can forget to invalidate.
-- Per-user data (user overrides, group membership, roles, privileges) is
-- not cached and has no trigger.
--
-- No foreign keys on purpose: a tenant delete cascades into the tables
-- below, and the counter write must not fail it.
-- ============================================================

CREATE TABLE menu_catalog_version (
    application_id UUID        NOT NULL,
    tenant_id      UUID,
    version        BIGINT      NOT NULL DEFAULT 0,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_menu_catalog_version
    ON menu_catalog_version (application_id,
                             (COALESCE(tenant_id, '00000000-0000-0000-0000-000000000000'::uuid)));

CREATE FUNCTION menu_catalog_bump(p_application UUID, p_tenant UUID) RETURNS VOID AS $$
    INSERT INTO menu_catalog_version (application_id, tenant_id, version)
    VALUES (p_application, p_tenant, 1)
    ON CONFLICT (application_id, (COALESCE(tenant_id, '00000000-0000-0000-0000-000000000000'::uuid)))
    DO UPDATE SET version = menu_catalog_version.version + 1, updated_at = now();
$$ LANGUAGE sql;

-- ── menu_items: its own scope ──────────────────────────────────────────
CREATE FUNCTION trg_menu_items_bump() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        PERFORM menu_catalog_bump(OLD.application_id, OLD.tenant_id);
    ELSE
        PERFORM menu_catalog_bump(NEW.application_id, NEW.tenant_id);
        IF TG_OP = 'UPDATE' AND (OLD.application_id, OLD.tenant_id) IS DISTINCT FROM (NEW.application_id, NEW.tenant_id) THEN
            PERFORM menu_catalog_bump(OLD.application_id, OLD.tenant_id);
        END IF;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_menu_items_bump
    AFTER INSERT OR UPDATE OR DELETE ON menu_items
    FOR EACH ROW EXECUTE FUNCTION trg_menu_items_bump();

-- ── tables hanging off an item: the item's scope ───────────────────────
CREATE FUNCTION trg_menu_item_child_bump() RETURNS trigger AS $$
DECLARE
    v_item UUID;
    v_app UUID;
    v_tenant UUID;
BEGIN
    v_item := CASE WHEN TG_OP = 'DELETE' THEN OLD.menu_item_id ELSE NEW.menu_item_id END;
    SELECT application_id, tenant_id INTO v_app, v_tenant FROM menu_items WHERE id = v_item;
    IF v_app IS NOT NULL THEN
        PERFORM menu_catalog_bump(v_app, v_tenant);
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_menu_plan_access_bump
    AFTER INSERT OR UPDATE OR DELETE ON menu_plan_access
    FOR EACH ROW EXECUTE FUNCTION trg_menu_item_child_bump();

CREATE TRIGGER trg_menu_item_translations_bump
    AFTER INSERT OR UPDATE OR DELETE ON menu_item_translations
    FOR EACH ROW EXECUTE FUNCTION trg_menu_item_child_bump();

-- A grant of a tenant's own role affects only that tenant, even on a global item.
CREATE FUNCTION trg_menu_role_access_bump() RETURNS trigger AS $$
DECLARE
    v_item UUID;
    v_role UUID;
    v_app UUID;
    v_item_tenant UUID;
    v_role_tenant UUID;
BEGIN
    v_item := CASE WHEN TG_OP = 'DELETE' THEN OLD.menu_item_id ELSE NEW.menu_item_id END;
    v_role := CASE WHEN TG_OP = 'DELETE' THEN OLD.role_id ELSE NEW.role_id END;
    SELECT application_id, tenant_id INTO v_app, v_item_tenant FROM menu_items WHERE id = v_item;
    SELECT tenant_id INTO v_role_tenant FROM roles WHERE id = v_role;
    IF v_app IS NOT NULL THEN
        PERFORM menu_catalog_bump(v_app, COALESCE(v_item_tenant, v_role_tenant));
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_menu_role_access_bump
    AFTER INSERT OR UPDATE OR DELETE ON menu_role_access
    FOR EACH ROW EXECUTE FUNCTION trg_menu_role_access_bump();

-- ── tenant-keyed tables: that tenant ───────────────────────────────────
CREATE FUNCTION trg_menu_tenant_overrides_bump() RETURNS trigger AS $$
DECLARE
    v_item UUID;
    v_tenant UUID;
    v_app UUID;
BEGIN
    v_item := CASE WHEN TG_OP = 'DELETE' THEN OLD.menu_item_id ELSE NEW.menu_item_id END;
    v_tenant := CASE WHEN TG_OP = 'DELETE' THEN OLD.tenant_id ELSE NEW.tenant_id END;
    SELECT application_id INTO v_app FROM menu_items WHERE id = v_item;
    IF v_app IS NOT NULL THEN
        PERFORM menu_catalog_bump(v_app, v_tenant);
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_menu_tenant_overrides_bump
    AFTER INSERT OR UPDATE OR DELETE ON menu_tenant_overrides
    FOR EACH ROW EXECUTE FUNCTION trg_menu_tenant_overrides_bump();

CREATE FUNCTION trg_menu_group_access_bump() RETURNS trigger AS $$
DECLARE
    v_item UUID;
    v_group UUID;
    v_app UUID;
    v_tenant UUID;
BEGIN
    v_item := CASE WHEN TG_OP = 'DELETE' THEN OLD.menu_item_id ELSE NEW.menu_item_id END;
    v_group := CASE WHEN TG_OP = 'DELETE' THEN OLD.group_id ELSE NEW.group_id END;
    SELECT application_id INTO v_app FROM menu_items WHERE id = v_item;
    SELECT tenant_id INTO v_tenant FROM user_groups WHERE id = v_group;
    IF v_app IS NOT NULL THEN
        PERFORM menu_catalog_bump(v_app, v_tenant);
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_menu_group_access_bump
    AFTER INSERT OR UPDATE OR DELETE ON menu_group_access
    FOR EACH ROW EXECUTE FUNCTION trg_menu_group_access_bump();

-- Existing rows start at version 1 so the first read is well-defined.
INSERT INTO menu_catalog_version (application_id, tenant_id, version)
SELECT id, NULL, 1 FROM applications
ON CONFLICT DO NOTHING;
