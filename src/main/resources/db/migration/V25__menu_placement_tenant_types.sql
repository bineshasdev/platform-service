-- ============================================================
-- V25 — Menu placement and tenant-type gate (Stage 8a)
--   * menu_items.placement     — where an item renders (left nav, header, settings hub …)
--   * menu_items.tenant_types  — tenant types the item applies to; NULL = all
-- Both columns are on menu_items, so trg_menu_items_bump (V23) already
-- invalidates cached catalogues when they change.
-- ============================================================

ALTER TABLE menu_items
    ADD COLUMN placement    VARCHAR(20) NOT NULL DEFAULT 'LEFT',
    ADD COLUMN tenant_types TEXT[];

ALTER TABLE menu_items
    ADD CONSTRAINT chk_menu_items_placement
        CHECK (placement IN ('LEFT','HEADER','QUICK','USER','SETTINGS','TAB','INNER','TILE')),
    -- Same values as chk_tenants_type (V10). An empty array would hide the item
    -- from everyone; "all tenant types" is NULL.
    ADD CONSTRAINT chk_menu_items_tenant_types
        CHECK (tenant_types IS NULL
               OR (cardinality(tenant_types) > 0
                   AND tenant_types <@ ARRAY['CLINIC','LAB','STORE','PRIVATE_CLINIC']::TEXT[])),
    -- A tenant's own item already applies to one tenant only.
    ADD CONSTRAINT chk_menu_items_tenant_types_global
        CHECK (tenant_id IS NULL OR tenant_types IS NULL);

-- Existing items (V5, V16) take the default LEFT until the catalogue seed re-places them.
