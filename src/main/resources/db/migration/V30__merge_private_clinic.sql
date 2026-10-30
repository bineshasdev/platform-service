-- ============================================================
-- V30 — One clinic type (authorization architecture, step 0)
--
-- PRIVATE_CLINIC is retired: a solo practice is a CLINIC whose
-- owner holds Doctor plus Clinic admin. Tenants and menu items
-- move to CLINIC and the type checks lose the value, so the
-- authorization templates (V31) exist for exactly CLINIC, LAB, STORE.
-- See Docs/design/authorization-architecture.md §18.1.
-- ============================================================

-- ── Tenants ──────────────────────────────────────────────────────────
UPDATE tenants SET type = 'CLINIC' WHERE type = 'PRIVATE_CLINIC';

ALTER TABLE tenants DROP CONSTRAINT IF EXISTS chk_tenants_type;
ALTER TABLE tenants
    ADD CONSTRAINT chk_tenants_type
        CHECK (type IS NULL OR type IN ('CLINIC','LAB','STORE'));

-- ── Menu items ───────────────────────────────────────────────────────
-- Drop the value; an item that applied only to private clinics now
-- applies to clinics. Duplicates cannot arise: CLINIC is added only
-- where it was absent.
UPDATE menu_items
   SET tenant_types = CASE
           WHEN 'CLINIC' = ANY(tenant_types) THEN array_remove(tenant_types, 'PRIVATE_CLINIC')
           ELSE array_replace(tenant_types, 'PRIVATE_CLINIC', 'CLINIC')
       END
 WHERE 'PRIVATE_CLINIC' = ANY(tenant_types);

ALTER TABLE menu_items DROP CONSTRAINT IF EXISTS chk_menu_items_tenant_types;
ALTER TABLE menu_items
    ADD CONSTRAINT chk_menu_items_tenant_types
        CHECK (tenant_types IS NULL
               OR (cardinality(tenant_types) > 0
                   AND tenant_types <@ ARRAY['CLINIC','LAB','STORE']::TEXT[]));

-- ── Signup lookup ────────────────────────────────────────────────────
UPDATE lookup_values v
   SET is_active = false
  FROM lookup_categories c
 WHERE v.category_id = c.id
   AND c.code = 'tenant_types'
   AND v.code = 'PRIVATE_CLINIC';

UPDATE lookup_values v
   SET label = 'Clinic or solo practice'
  FROM lookup_categories c
 WHERE v.category_id = c.id
   AND c.code = 'tenant_types'
   AND v.code = 'CLINIC';
