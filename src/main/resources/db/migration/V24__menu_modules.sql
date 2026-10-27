-- ============================================================
-- V24 — Module menu registration (menu stage 6)
--
--   menu_modules           a registered service that contributes menu items
--   menu_module_manifests  every manifest received, accepted or not (audit + diagnosis)
--   menu_items.module_id   owner of a source = 'MODULE' item
--
-- Module items are MATERIALISED into menu_items (global, tenant NULL) and
-- namespaced "<module>.<key>", so a page load never calls a module and a
-- module outage leaves the last accepted menu in place.
--
-- A module is identified by its own Keycloak client (owner_client_id); only
-- a client-credentials token of that client may push its manifest.
-- ============================================================

CREATE TABLE menu_modules (
    id                    UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    code                  TEXT        NOT NULL UNIQUE
                              CHECK (code ~ '^[a-z][a-z0-9_-]{1,31}$'),
    name                  TEXT        NOT NULL,
    description           TEXT,
    application_id        UUID        NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    owner_client_id       TEXT        NOT NULL,
    -- Optional: where the platform may PULL the manifest (admin refresh + slow schedule).
    manifest_url          TEXT,
    -- Every route a module item uses must sit under one of these path prefixes.
    allowed_route_prefixes TEXT[]     NOT NULL,
    -- privileges.module_code this module may reference (shared platform privileges are always allowed).
    privilege_module_code TEXT,
    status                TEXT        NOT NULL DEFAULT 'PENDING'
                              CHECK (status IN ('PENDING', 'ACTIVE', 'SUSPENDED', 'DEREGISTERED')),
    health                TEXT        NOT NULL DEFAULT 'UNKNOWN'
                              CHECK (health IN ('UNKNOWN', 'UP', 'STALE', 'DOWN')),
    -- Last ACCEPTED manifest: the last-known-good the menu is built from.
    current_version       TEXT,
    current_hash          TEXT,
    manifest              JSONB,
    last_synced_at        TIMESTAMPTZ,
    last_attempt_at       TIMESTAMPTZ,
    next_attempt_at       TIMESTAMPTZ,
    last_error            TEXT,
    consecutive_failures  INT         NOT NULL DEFAULT 0,
    created_by            UUID,
    updated_by            UUID,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- One live module per Keycloak client; a deregistered module frees its client.
CREATE UNIQUE INDEX uq_menu_modules_client
    ON menu_modules (owner_client_id) WHERE status <> 'DEREGISTERED';

CREATE INDEX idx_menu_modules_due
    ON menu_modules (next_attempt_at) WHERE status = 'ACTIVE' AND manifest_url IS NOT NULL;

CREATE TABLE menu_module_manifests (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    module_id    UUID        NOT NULL REFERENCES menu_modules(id) ON DELETE CASCADE,
    received_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    received_via TEXT        NOT NULL CHECK (received_via IN ('PUSH', 'PULL')),
    client_id    TEXT,
    outcome      TEXT        NOT NULL CHECK (outcome IN ('ACCEPTED', 'REJECTED', 'FAILED')),
    version      TEXT,
    content_hash TEXT,
    manifest     JSONB,
    errors       JSONB,
    summary      JSONB,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_menu_module_manifests_module ON menu_module_manifests (module_id, received_at DESC);

ALTER TABLE menu_items
    ADD COLUMN module_id UUID REFERENCES menu_modules(id) ON DELETE CASCADE;

-- A MODULE item has an owner; nothing else does.
ALTER TABLE menu_items
    ADD CONSTRAINT chk_menu_items_module CHECK ((source = 'MODULE') = (module_id IS NOT NULL));

CREATE INDEX idx_menu_items_module ON menu_items (module_id) WHERE module_id IS NOT NULL;
