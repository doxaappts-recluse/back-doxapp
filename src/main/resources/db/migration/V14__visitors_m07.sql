-- V14 · M07 Visitantes y consolidación (base).
-- Primer módulo CONTRATABLE con pantallas: solo lo ven las organizaciones cuyo contrato lo incluye.
-- Una persona puede registrarse sin documento (un visitante rara vez lo da); el documento se completa después en Personas.

INSERT INTO module (code, name_es, name_en, levels, kind, actions, delegable, route, icon, sort_order) VALUES
 ('VISITOR', 'Visitantes', 'Visitors', ARRAY['N2','N3'], 'CONTRACTABLE', ARRAY['V','C','E','D','S','X'], TRUE, 'visitors', 'user-add', 150);

-- ---------------------------------------------------------------- persona sin documento
ALTER TABLE person ALTER COLUMN doc_type   DROP NOT NULL;
ALTER TABLE person ALTER COLUMN doc_number DROP NOT NULL;
ALTER TABLE person ADD CONSTRAINT ck_person_doc_pair CHECK ((doc_type IS NULL) = (doc_number IS NULL));

-- ---------------------------------------------------------------- reglas por organización
CREATE TABLE visitor_rules (
    organization_id            UUID PRIMARY KEY REFERENCES organization (id),
    integrated_min_attendances INT         NOT NULL DEFAULT 3,
    integrated_window_weeks    INT         NOT NULL DEFAULT 6,
    new_sla_hours              INT         NOT NULL DEFAULT 48,
    updated_at                 TIMESTAMPTZ,
    updated_by                 UUID,
    version                    BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_vr_att   CHECK (integrated_min_attendances >= 1),
    CONSTRAINT ck_vr_weeks CHECK (integrated_window_weeks >= 1),
    CONSTRAINT ck_vr_sla   CHECK (new_sla_hours >= 1)
);

-- ---------------------------------------------------------------- casos de visitante
CREATE TABLE visitor_case (
    id                UUID PRIMARY KEY,
    organization_id   UUID         NOT NULL REFERENCES organization (id),
    branch_id         UUID         NOT NULL REFERENCES branch (id),
    person_id         UUID         NOT NULL REFERENCES person (id),
    first_visit_date  DATE         NOT NULL,
    how_arrived       VARCHAR(60),
    invited_by        UUID         REFERENCES person (id),
    stage             VARCHAR(15)  NOT NULL DEFAULT 'NEW',
    consolidator_id   UUID         REFERENCES person (id),
    assigned_at       TIMESTAMPTZ,
    first_contact_at  TIMESTAMPTZ,
    last_contact_at   TIMESTAMPTZ,
    next_action_date  DATE,
    integrated_at     TIMESTAMPTZ,
    closed_at         TIMESTAMPTZ,
    archive_reason    VARCHAR(60),
    notes             VARCHAR(1000),
    source            VARCHAR(12)  NOT NULL DEFAULT 'STAFF',
    consent_status    VARCHAR(10)  NOT NULL DEFAULT 'PENDING',
    consent_at        TIMESTAMPTZ,
    sla_alerted_at    TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL,
    created_by        UUID,
    updated_at        TIMESTAMPTZ,
    updated_by        UUID,
    version           BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_vc_stage   CHECK (stage IN ('NEW','IN_FOLLOWUP','INTEGRATED','CONVERTED','ARCHIVED')),
    CONSTRAINT ck_vc_source  CHECK (source IN ('STAFF','PUBLIC_FORM','MEMBER_INVITE')),
    CONSTRAINT ck_vc_consent CHECK (consent_status IN ('GRANTED','PENDING'))
);
-- [V10] un caso abierto por persona y sede
CREATE UNIQUE INDEX ux_vc_open ON visitor_case (person_id, branch_id) WHERE stage IN ('NEW','IN_FOLLOWUP','INTEGRATED');
CREATE INDEX ix_vc_org_branch_stage ON visitor_case (organization_id, branch_id, stage);
CREATE INDEX ix_vc_consolidator     ON visitor_case (consolidator_id, stage);
CREATE INDEX ix_vc_person           ON visitor_case (person_id);
CREATE INDEX ix_vc_sla              ON visitor_case (stage, created_at) WHERE stage = 'NEW';

-- Contactos de seguimiento. Polimórfico (visitante hoy, persona en M12): se define una sola vez.
CREATE TABLE follow_up_contact (
    id                UUID PRIMARY KEY,
    organization_id   UUID         NOT NULL REFERENCES organization (id),
    subject_type      VARCHAR(15)  NOT NULL,
    subject_id        UUID         NOT NULL,
    at                TIMESTAMPTZ  NOT NULL,
    method            VARCHAR(10)  NOT NULL,
    result            VARCHAR(12)  NOT NULL,
    notes             VARCHAR(1000),
    next_action_date  DATE,
    by_person_id      UUID         REFERENCES person (id),
    created_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_fc_subject CHECK (subject_type IN ('VISITOR_CASE','PERSON')),
    CONSTRAINT ck_fc_method  CHECK (method IN ('CALL','WHATSAPP','VISIT','IN_PERSON','OTHER')),
    CONSTRAINT ck_fc_result  CHECK (result IN ('CONTACTED','NO_ANSWER','RESCHEDULED','REFUSED','OTHER'))
);
CREATE INDEX ix_fc_subject ON follow_up_contact (subject_type, subject_id, at DESC);

-- ---------------------------------------------------------------- catálogos
-- «Cómo nos conocieron» (VISITOR_SOURCE) ya viene sembrado desde M23; aquí solo se agrega el motivo de archivo.
INSERT INTO catalog_item (id, organization_id, type, code, name_es, name_en, sort_order, active, source, parent_id, created_at) VALUES
 (gen_random_uuid(), NULL, 'VISITOR_ARCHIVE_REASON', 'MOVED',            'Se mudó',                     'Moved away',                10, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'VISITOR_ARCHIVE_REASON', 'NOT_INTERESTED',   'No le interesó',              'Not interested',            20, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'VISITOR_ARCHIVE_REASON', 'NO_CONTACT',       'No se logró contactar',       'Could not be reached',      30, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'VISITOR_ARCHIVE_REASON', 'WRONG_DATA',       'Datos incorrectos',           'Wrong contact data',        40, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'VISITOR_ARCHIVE_REASON', 'ATTENDS_ELSEWHERE','Asiste a otra iglesia',      'Attends another church',    50, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'VISITOR_ARCHIVE_REASON', 'OTHER',            'Otro',                        'Other',                     60, TRUE, 'BASE', NULL, now());
