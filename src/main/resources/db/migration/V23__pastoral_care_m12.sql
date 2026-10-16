-- M12 · Cuidado pastoral: seguimiento de personas (PASTORAL_CARE) y peticiones de oración (PRAYER).
INSERT INTO module (code, name_es, name_en, levels, kind, actions, delegable, route, icon, sort_order) VALUES
 ('PASTORAL_CARE', 'Cuidado pastoral', 'Pastoral care', ARRAY['N2','N3'], 'CONTRACTABLE', ARRAY['V','C','E','S','A','X','H'], TRUE, 'pastoral-cases', 'heart', 270),
 ('PRAYER',        'Oración',          'Prayer',         ARRAY['N2','N3'], 'CONTRACTABLE', ARRAY['V','C','E','A','X'],       TRUE, 'prayer-requests', 'like', 275);

-- ---------------------------------------------------------------- casos pastorales
CREATE TABLE pastoral_case (
    id               UUID PRIMARY KEY,
    organization_id  UUID         NOT NULL REFERENCES organization (id),
    branch_id        UUID         NOT NULL REFERENCES branch (id),
    person_id        UUID         NOT NULL REFERENCES person (id),
    type             VARCHAR(20)  NOT NULL,
    priority         VARCHAR(6)   NOT NULL DEFAULT 'NORMAL',
    status           VARCHAR(12)  NOT NULL DEFAULT 'OPEN',
    assigned_to      UUID         REFERENCES person (id),
    source           VARCHAR(15)  NOT NULL DEFAULT 'MANUAL',
    confidentiality  VARCHAR(10)  NOT NULL DEFAULT 'STANDARD',
    due_at           TIMESTAMPTZ,
    result           VARCHAR(30),
    last_contact_at  TIMESTAMPTZ,
    resolved_at      TIMESTAMPTZ,
    closed_at        TIMESTAMPTZ,
    sla_alerted_at   TIMESTAMPTZ,
    sla_escalated_at TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL,
    created_by       UUID,
    updated_at       TIMESTAMPTZ  NOT NULL,
    updated_by       UUID,
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_pc_type   CHECK (type IN ('INACTIVE_FOLLOWUP','VISIT','COUNSELING','CRISIS','BEREAVEMENT','HOSPITAL','OTHER')),
    CONSTRAINT ck_pc_prio   CHECK (priority IN ('LOW','NORMAL','HIGH')),
    CONSTRAINT ck_pc_status CHECK (status IN ('OPEN','IN_PROGRESS','RESOLVED','CLOSED')),
    CONSTRAINT ck_pc_source CHECK (source IN ('AUTO_ABSENCE','MANUAL','PORTAL')),
    CONSTRAINT ck_pc_conf   CHECK (confidentiality IN ('STANDARD','RESTRICTED'))
);
CREATE INDEX ix_pc_org_branch ON pastoral_case (organization_id, branch_id, status);
CREATE INDEX ix_pc_person ON pastoral_case (person_id);
CREATE INDEX ix_pc_assigned ON pastoral_case (assigned_to);
-- [V8] una sola racha auto-abierta a la vez por persona (una nueva racha solo puede abrir otro caso una vez cerrado/resuelto el anterior)
CREATE UNIQUE INDEX ux_pc_auto_open ON pastoral_case (person_id) WHERE source = 'AUTO_ABSENCE' AND status IN ('OPEN', 'IN_PROGRESS');

CREATE TABLE case_note (
    id          UUID PRIMARY KEY,
    case_id     UUID         NOT NULL REFERENCES pastoral_case (id),
    author_id   UUID         REFERENCES person (id),
    text        TEXT         NOT NULL,
    restricted  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_cn_case ON case_note (case_id, created_at DESC);

CREATE TABLE case_assignment_history (
    id             UUID PRIMARY KEY,
    case_id        UUID         NOT NULL REFERENCES pastoral_case (id),
    from_person_id UUID         REFERENCES person (id),
    to_person_id   UUID         REFERENCES person (id),
    reason         VARCHAR(300) NOT NULL,
    by_person_id   UUID         REFERENCES person (id),
    at             TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_cah_case ON case_assignment_history (case_id, at DESC);

-- follow_up_contact ya existe (M07, V14): se reutiliza para los contactos de un caso pastoral, no se crea una tabla nueva.
ALTER TABLE follow_up_contact DROP CONSTRAINT ck_fc_subject;
ALTER TABLE follow_up_contact ADD CONSTRAINT ck_fc_subject CHECK (subject_type IN ('VISITOR_CASE', 'PERSON', 'PASTORAL_CASE'));

CREATE TABLE pastoral_rules (
    organization_id  UUID    PRIMARY KEY REFERENCES organization (id),
    absence_weeks    INT     NOT NULL DEFAULT 4,
    sla_hours_high   INT     NOT NULL DEFAULT 24,
    sla_hours_normal INT     NOT NULL DEFAULT 72,
    sla_hours_low    INT     NOT NULL DEFAULT 168,
    version          BIGINT  NOT NULL DEFAULT 0
);

-- ---------------------------------------------------------------- oración
CREATE TABLE prayer_request (
    id             UUID         PRIMARY KEY,
    organization_id UUID        NOT NULL REFERENCES organization (id),
    branch_id      UUID         REFERENCES branch (id),
    requested_by   UUID         NOT NULL REFERENCES person (id),
    for_person_id  UUID         REFERENCES person (id),
    text           VARCHAR(1000) NOT NULL,
    category       VARCHAR(30),
    visibility     VARCHAR(12)  NOT NULL DEFAULT 'PRIVATE',
    anonymous      BOOLEAN      NOT NULL DEFAULT FALSE,
    moderation     VARCHAR(10)  NOT NULL DEFAULT 'NA',
    moderated_by   UUID         REFERENCES person (id),
    moderated_at   TIMESTAMPTZ,
    reject_reason  VARCHAR(300),
    status         VARCHAR(12)  NOT NULL DEFAULT 'OPEN',
    answered_at    TIMESTAMPTZ,
    testimony      VARCHAR(1000),
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_pr_visibility CHECK (visibility IN ('PRIVATE','LEADERS','CONGREGATION')),
    CONSTRAINT ck_pr_moderation CHECK (moderation IN ('NA','PENDING','APPROVED','REJECTED')),
    CONSTRAINT ck_pr_status     CHECK (status IN ('OPEN','IN_PROGRESS','ANSWERED','CLOSED')),
    CONSTRAINT ck_pr_text       CHECK (char_length(text) BETWEEN 5 AND 1000)
);
CREATE INDEX ix_pr_org ON prayer_request (organization_id, branch_id, status);
CREATE INDEX ix_pr_wall ON prayer_request (organization_id, visibility, moderation) WHERE visibility = 'CONGREGATION';

CREATE TABLE prayer_support (
    id          UUID        PRIMARY KEY,
    request_id  UUID        NOT NULL REFERENCES prayer_request (id),
    person_id   UUID        NOT NULL REFERENCES person (id),
    at          TIMESTAMPTZ NOT NULL,
    UNIQUE (request_id, person_id)
);

-- ---------------------------------------------------------------- catálogos (M23)
INSERT INTO catalog_item (id, organization_id, type, code, name_es, name_en, sort_order, active, source, parent_id, created_at) VALUES
 (gen_random_uuid(), NULL, 'PRAYER_CATEGORY', 'HEALTH',     'Salud',            'Health',            10, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'PRAYER_CATEGORY', 'FAMILY',     'Familia',          'Family',            20, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'PRAYER_CATEGORY', 'PROVISION',  'Provisión/Trabajo','Provision/Work',    30, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'PRAYER_CATEGORY', 'SPIRITUAL',  'Vida espiritual',  'Spiritual life',    40, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'PRAYER_CATEGORY', 'THANKS',     'Gratitud',         'Thanksgiving',      50, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'PRAYER_CATEGORY', 'OTHER',      'Otro',             'Other',             60, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'CASE_RESULT', 'RECONNECTED',    'Se restableció el contacto',        'Contact re-established',   10, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'CASE_RESULT', 'REINTEGRATED',   'Se integró de nuevo',                'Re-integrated',             20, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'CASE_RESULT', 'REFERRED',       'Se derivó a otro ministerio',        'Referred to another area',  30, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'CASE_RESULT', 'NO_RESPONSE',    'Sin respuesta',                       'No response',               40, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'CASE_RESULT', 'MOVED_AWAY',     'Se mudó / dejó de asistir',           'Moved away / stopped attending', 50, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'CASE_RESULT', 'OTHER',          'Otro',                                'Other',                     60, TRUE, 'BASE', NULL, now());
