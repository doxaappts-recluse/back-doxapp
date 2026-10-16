-- V17 · M21 Solicitudes y reglas entre sedes: motor único de aprobaciones, traslados de sede y visibilidad de datos entre sedes.
-- La solicitud de la persona (portal N4, MY_REQUESTS) llega con M24; las solicitudes de soporte (M22) usarán el mismo motor.

INSERT INTO module (code, name_es, name_en, levels, kind, actions, delegable, route, icon, sort_order) VALUES
 ('APPROVAL_INBOX',   'Bandeja de solicitudes', 'Approvals inbox',     ARRAY['N2','N3'], 'BASE',         ARRAY['V'],                 TRUE, 'approvals', 'inbox',    160),
 ('BRANCH_TRANSFER',  'Traslados de sede',      'Branch transfers',    ARRAY['N2','N3'], 'CONTRACTABLE', ARRAY['V','C','E','A'],     TRUE, 'transfers', 'swap',     170),
 ('VISIBILITY_RULES', 'Visibilidad entre sedes', 'Cross-branch access', ARRAY['N2','N3'], 'CONTRACTABLE', ARRAY['V','C','E','A'],     TRUE, 'visibility', 'eye',     180);

-- ---------------------------------------------------------------- motor de aprobaciones (núcleo 01 §3)
CREATE TABLE approval_request (
    id                 UUID PRIMARY KEY,
    organization_id    UUID         NOT NULL REFERENCES organization (id),
    branch_id          UUID         NOT NULL REFERENCES branch (id),      -- sede que decide
    related_branch_id  UUID         REFERENCES branch (id),               -- la otra sede involucrada (origen del traslado, sede solicitante)
    type               VARCHAR(40)  NOT NULL,
    subject_type       VARCHAR(30)  NOT NULL,
    subject_id         UUID         NOT NULL,
    requested_by       UUID         NOT NULL REFERENCES person (id),
    status             VARCHAR(12)  NOT NULL DEFAULT 'PENDING',
    reason             VARCHAR(500),
    waiting_info       BOOLEAN      NOT NULL DEFAULT FALSE,
    decided_by         UUID         REFERENCES person (id),
    decided_at         TIMESTAMPTZ,
    decision_note      VARCHAR(500),
    expires_at         TIMESTAMPTZ,
    reminded_at        TIMESTAMPTZ,
    payload            JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at         TIMESTAMPTZ  NOT NULL,
    created_by         UUID,
    updated_at         TIMESTAMPTZ,
    updated_by         UUID,
    version            BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_ar_status CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELLED','EXPIRED'))
);
CREATE INDEX ix_ar_org_status ON approval_request (organization_id, status, branch_id);
CREATE INDEX ix_ar_related    ON approval_request (organization_id, related_branch_id);
CREATE INDEX ix_ar_requester  ON approval_request (organization_id, requested_by);
CREATE INDEX ix_ar_subject    ON approval_request (subject_type, subject_id);

CREATE TABLE approval_comment (
    id          UUID PRIMARY KEY,
    request_id  UUID          NOT NULL REFERENCES approval_request (id),
    author_id   UUID          NOT NULL REFERENCES person (id),
    kind        VARCHAR(15)   NOT NULL DEFAULT 'COMMENT',
    body        VARCHAR(1000) NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_ac_kind CHECK (kind IN ('COMMENT','INFO_REQUEST'))
);
CREATE INDEX ix_ac_request ON approval_comment (request_id, created_at);

-- ---------------------------------------------------------------- traslados de sede
CREATE TABLE branch_transfer (
    id               UUID PRIMARY KEY,
    organization_id  UUID         NOT NULL REFERENCES organization (id),
    request_id       UUID         NOT NULL UNIQUE REFERENCES approval_request (id),
    person_id        UUID         NOT NULL REFERENCES person (id),
    from_branch_id   UUID         NOT NULL REFERENCES branch (id),
    to_branch_id     UUID         NOT NULL REFERENCES branch (id),
    reason           VARCHAR(300),
    effective_date   DATE         NOT NULL,
    options          JSONB        NOT NULL DEFAULT '{}'::jsonb,
    status           VARCHAR(12)  NOT NULL DEFAULT 'PENDING',
    forced           BOOLEAN      NOT NULL DEFAULT FALSE,
    executed_at      TIMESTAMPTZ,
    executed_by      UUID,
    created_at       TIMESTAMPTZ  NOT NULL,
    created_by       UUID,
    updated_at       TIMESTAMPTZ,
    updated_by       UUID,
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_bt_status CHECK (status IN ('PENDING','APPROVED','EXECUTED','REJECTED','CANCELLED','EXPIRED')),
    CONSTRAINT ck_bt_branches CHECK (from_branch_id <> to_branch_id)
);
-- [V6] una sola solicitud abierta por persona
CREATE UNIQUE INDEX ux_bt_open ON branch_transfer (person_id) WHERE status IN ('PENDING','APPROVED');
CREATE INDEX ix_bt_org ON branch_transfer (organization_id, status);
CREATE INDEX ix_bt_due ON branch_transfer (effective_date) WHERE status = 'APPROVED';

-- ---------------------------------------------------------------- visibilidad entre sedes
CREATE TABLE data_access_rule (
    id               UUID PRIMARY KEY,
    organization_id  UUID         NOT NULL REFERENCES organization (id),
    module_code      VARCHAR(40)  NOT NULL,
    scope            VARCHAR(20)  NOT NULL,
    enabled          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ  NOT NULL,
    created_by       UUID,
    updated_at       TIMESTAMPTZ,
    updated_by       UUID,
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_dar_scope CHECK (scope IN ('CURRENT_BRANCH','PERSON_HISTORY','ORGANIZATION','APPROVAL_REQUIRED')),
    CONSTRAINT ux_dar UNIQUE (organization_id, module_code)
);

CREATE TABLE visibility_grant (
    id                UUID PRIMARY KEY,
    organization_id   UUID         NOT NULL REFERENCES organization (id),
    person_id         UUID         NOT NULL REFERENCES person (id),
    source_branch_id  UUID         NOT NULL REFERENCES branch (id),     -- sede dueña de los datos
    target_branch_id  UUID         NOT NULL REFERENCES branch (id),     -- sede que puede ver
    module_code       VARCHAR(40)  NOT NULL,
    request_id        UUID         REFERENCES approval_request (id),
    visible_until     DATE         NOT NULL,
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    approved_by       UUID,
    approved_at       TIMESTAMPTZ,
    revoked_by        UUID,
    revoked_at        TIMESTAMPTZ,
    revoke_reason     VARCHAR(300),
    expired_at        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_vg_branches CHECK (source_branch_id <> target_branch_id)
);
CREATE UNIQUE INDEX ux_vg_active ON visibility_grant (person_id, target_branch_id, module_code) WHERE active;
CREATE INDEX ix_vg_target ON visibility_grant (target_branch_id, module_code) WHERE active;
CREATE INDEX ix_vg_org ON visibility_grant (organization_id, active, visible_until);
