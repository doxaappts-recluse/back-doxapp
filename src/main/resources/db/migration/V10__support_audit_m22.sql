-- V10 · M22 Soporte, auditoría y anuncios de plataforma (base).
-- Casos de soporte con hilo (notas internas solo para plataforma) y adjuntos, políticas de retención de auditoría con
-- función de purga controlada, y anuncios de plataforma. El acceso asistido queda para cuando exista el motor de solicitudes (M21).

-- ---------------------------------------------------------------- módulos
INSERT INTO module (code, name_es, name_en, levels, kind, actions, delegable, route, icon, sort_order) VALUES
 ('SUPPORT',                'Soporte',                'Support',                ARRAY['N1','N2','N3'], 'BASE', ARRAY['V','C','E','S','T'], TRUE,  'support',       'customer-service', 400),
 ('AUDIT_LOG',              'Auditoría',              'Audit log',              ARRAY['N1','N2','N3'], 'BASE', ARRAY['V','X','E'],         FALSE, 'audit',         'audit',            410),
 ('PLATFORM_ANNOUNCEMENTS', 'Anuncios de plataforma', 'Platform announcements', ARRAY['N1'],            'BASE', ARRAY['V','C','E','S'],     FALSE, 'announcements', 'notification',     420);

-- ---------------------------------------------------------------- casos de soporte
CREATE SEQUENCE support_case_seq START 1;

CREATE TABLE support_case (
    id                    UUID PRIMARY KEY,
    case_number           BIGINT       NOT NULL DEFAULT nextval('support_case_seq') UNIQUE,
    organization_id       UUID         NOT NULL REFERENCES organization (id),
    branch_id             UUID         REFERENCES branch (id),
    opened_by             UUID         NOT NULL REFERENCES person (id),
    opened_by_name        VARCHAR(170) NOT NULL,
    assignee_id           UUID         REFERENCES platform_staff (id),
    category              VARCHAR(20)  NOT NULL,
    priority              VARCHAR(10)  NOT NULL DEFAULT 'NORMAL',
    subject               VARCHAR(150) NOT NULL,
    status                VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    sla_due_at            TIMESTAMPTZ  NOT NULL,
    first_response_at     TIMESTAMPTZ,
    sla_alerted_at        TIMESTAMPTZ,
    last_activity_at      TIMESTAMPTZ  NOT NULL,
    resolved_at           TIMESTAMPTZ,
    closed_at             TIMESTAMPTZ,
    satisfaction          SMALLINT,
    satisfaction_comment  VARCHAR(500),
    created_at            TIMESTAMPTZ  NOT NULL,
    created_by            UUID,
    updated_at            TIMESTAMPTZ,
    updated_by            UUID,
    version               BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_sc_category CHECK (category IN ('HOW_TO','BUG','BILLING','CONTRACT_CHANGE','NEW_BRANCH','DATA_REQUEST','OTHER')),
    CONSTRAINT ck_sc_priority CHECK (priority IN ('LOW','NORMAL','HIGH','URGENT')),
    CONSTRAINT ck_sc_status   CHECK (status IN ('OPEN','WAITING_ORG','WAITING_PLATFORM','RESOLVED','CLOSED')),
    CONSTRAINT ck_sc_rating   CHECK (satisfaction IS NULL OR satisfaction BETWEEN 1 AND 5)
);
CREATE INDEX ix_sc_org_status ON support_case (organization_id, status, last_activity_at DESC);
CREATE INDEX ix_sc_branch     ON support_case (branch_id);
CREATE INDEX ix_sc_opened_by  ON support_case (opened_by);
CREATE INDEX ix_sc_assignee   ON support_case (assignee_id, status);
CREATE INDEX ix_sc_status_sla ON support_case (status, sla_due_at);

-- Hilo del caso. internal = nota solo de plataforma (nunca se serializa hacia la organización, V5).
-- kind: MESSAGE (texto de una persona) | EVENT (cambio de estado o asignación registrado en el hilo).
CREATE TABLE support_message (
    id           UUID PRIMARY KEY,
    case_id      UUID         NOT NULL REFERENCES support_case (id),
    kind         VARCHAR(10)  NOT NULL DEFAULT 'MESSAGE',
    author_type  VARCHAR(10)  NOT NULL,
    author_id    UUID,
    author_name  VARCHAR(170) NOT NULL,
    body         VARCHAR(4000) NOT NULL,
    internal     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_sm_kind   CHECK (kind IN ('MESSAGE','EVENT')),
    CONSTRAINT ck_sm_author CHECK (author_type IN ('STAFF','PERSON','SYSTEM'))
);
CREATE INDEX ix_sm_case ON support_message (case_id, created_at);

CREATE TABLE support_attachment (
    id            UUID PRIMARY KEY,
    message_id    UUID         NOT NULL REFERENCES support_message (id),
    case_id       UUID         NOT NULL REFERENCES support_case (id),
    file_name     VARCHAR(160) NOT NULL,
    content_type  VARCHAR(80)  NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    storage_key   VARCHAR(300) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_sa_message ON support_attachment (message_id);
CREATE INDEX ix_sa_case    ON support_attachment (case_id);

-- ---------------------------------------------------------------- retención de auditoría
-- organization_id NULL = valor por defecto de plataforma; module_code NULL = toda la organización.
CREATE TABLE retention_policy (
    id               UUID PRIMARY KEY,
    organization_id  UUID REFERENCES organization (id),
    module_code      VARCHAR(40),
    retain_months    INT         NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL,
    created_by       UUID,
    updated_at       TIMESTAMPTZ,
    updated_by       UUID,
    version          BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_rp_months CHECK (retain_months BETWEEN 1 AND 240)
);
CREATE UNIQUE INDEX ux_rp_scope ON retention_policy (COALESCE(organization_id, '00000000-0000-0000-0000-000000000000'::uuid), COALESCE(module_code, '*'));

-- El trigger sigue bloqueando todo UPDATE y todo DELETE, salvo el DELETE que ejecuta audit_purge (V6: solo la purga por retención).
CREATE OR REPLACE FUNCTION audit_event_immutable() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' AND current_setting('doxapp.audit_purge', true) = 'on' THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'audit_event es de solo anexar (append-only)';
END;
$$ LANGUAGE plpgsql;

-- Único camino de borrado: eventos de (organización, módulo) anteriores al corte. La aplicación registra la purga en la auditoría.
CREATE FUNCTION audit_purge(p_org UUID, p_module VARCHAR, p_before TIMESTAMPTZ) RETURNS BIGINT AS $$
DECLARE
    n BIGINT;
BEGIN
    PERFORM set_config('doxapp.audit_purge', 'on', true);
    DELETE FROM audit_event
     WHERE at < p_before
       AND organization_id IS NOT DISTINCT FROM p_org
       AND module_code = p_module;
    GET DIAGNOSTICS n = ROW_COUNT;
    PERFORM set_config('doxapp.audit_purge', 'off', true);
    RETURN n;
END;
$$ LANGUAGE plpgsql;

-- ---------------------------------------------------------------- anuncios de plataforma
CREATE TABLE platform_announcement (
    id               UUID PRIMARY KEY,
    title            VARCHAR(120)  NOT NULL,
    body             VARCHAR(2000) NOT NULL,
    severity         VARCHAR(12)   NOT NULL,
    audience_type    VARCHAR(10)   NOT NULL,
    audience_ids     UUID[]        NOT NULL DEFAULT '{}',
    starts_at        TIMESTAMPTZ   NOT NULL,
    ends_at          TIMESTAMPTZ   NOT NULL,
    dismissible      BOOLEAN       NOT NULL DEFAULT TRUE,
    portal_visible   BOOLEAN       NOT NULL DEFAULT FALSE,
    status           VARCHAR(12)   NOT NULL DEFAULT 'DRAFT',
    published_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ   NOT NULL,
    created_by       UUID,
    updated_at       TIMESTAMPTZ,
    updated_by       UUID,
    version          BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_pa_severity CHECK (severity IN ('INFO','MAINTENANCE','INCIDENT')),
    CONSTRAINT ck_pa_audience CHECK (audience_type IN ('ALL','ORGS','PLANS')),
    CONSTRAINT ck_pa_status   CHECK (status IN ('DRAFT','PUBLISHED','CANCELLED')),
    CONSTRAINT ck_pa_range    CHECK (ends_at > starts_at)
);
CREATE INDEX ix_pa_status_dates ON platform_announcement (status, starts_at, ends_at);
