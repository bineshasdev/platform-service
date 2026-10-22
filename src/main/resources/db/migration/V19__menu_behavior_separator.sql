-- ============================================================
-- V19 — Menu item behaviour + separators (menu stage 1)
--
--   behavior             what activating the item does:
--                          NAVIGATE  go to route
--                          EXPAND    toggle its children
--                          BOTH      navigate and expand
--   is_separator_before  render a divider line above the item
--
-- Existing rows: anything that has children (or is a GROUP) is EXPAND,
-- every leaf is NAVIGATE. BOTH is only ever set deliberately by an admin.
-- ============================================================

ALTER TABLE menu_items
    ADD COLUMN behavior            TEXT    NOT NULL DEFAULT 'NAVIGATE'
                                       CHECK (behavior IN ('NAVIGATE', 'EXPAND', 'BOTH')),
    ADD COLUMN is_separator_before BOOLEAN NOT NULL DEFAULT false;

UPDATE menu_items m
   SET behavior = 'EXPAND'
 WHERE m.type = 'GROUP'
    OR EXISTS (SELECT 1 FROM menu_items c WHERE c.parent_id = m.id);
