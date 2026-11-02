-- ============================================================
-- V35 — Authorization manifests (authorization architecture, phase 4)
--
--   authz_manifests           every delivery of a service's privilege manifest
--   authz_template_proposals  template grants a service proposes; an operator decides
-- See Docs/design/authorization-architecture.md §21.
-- ============================================================

CREATE TABLE authz_manifests (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    service      TEXT        NOT NULL,
    version      TEXT,
    content_hash TEXT        NOT NULL,
    client_id    TEXT        NOT NULL,
    outcome      TEXT        NOT NULL CHECK (outcome IN ('ACCEPTED','REJECTED')),
    errors       JSONB,
    summary      JSONB,
    received_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_authz_manifests_service ON authz_manifests(service, received_at DESC);

CREATE TABLE authz_template_proposals (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    service        TEXT        NOT NULL,
    template_id    UUID        NOT NULL REFERENCES authz_role_templates(id) ON DELETE CASCADE,
    privilege_code TEXT        NOT NULL REFERENCES authz_privileges(code) ON UPDATE CASCADE,
    status         TEXT        NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','APPROVED','REJECTED')),
    manifest_id    UUID        REFERENCES authz_manifests(id) ON DELETE SET NULL,
    proposed_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_by     UUID,
    decided_at     TIMESTAMPTZ,
    reason         TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- One open proposal per template and privilege.
CREATE UNIQUE INDEX uq_authz_template_proposals_pending
    ON authz_template_proposals(template_id, privilege_code) WHERE status = 'PENDING';
CREATE INDEX idx_authz_template_proposals_status ON authz_template_proposals(status, proposed_at);
