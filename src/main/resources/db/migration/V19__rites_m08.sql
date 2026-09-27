-- V19 · M08 Membresía y ritos: membresía, bautizo, matrimonio y presentación de niños, con requisitos, aprobación (M21) y certificado.
-- Los tres ritos comparten la tabla rite (tipo BAPTISM | MARRIAGE | DEDICATION); la membresía tiene la suya porque tiene periodos.

INSERT INTO module (code, name_es, name_en, levels, kind, actions, delegable, route, icon, sort_order) VALUES
 ('MEMBERSHIP',      'Membresía',              'Membership',       ARRAY['N2','N3'], 'CONTRACTABLE', ARRAY['V','C','E','S','A','X','H','O'],     TRUE, 'memberships',        'idcard', 210),
 ('BAPTISM',         'Bautismos',              'Baptisms',         ARRAY['N2','N3'], 'CONTRACTABLE', ARRAY['V','C','E','D','S','A','X','O'],     TRUE, 'baptisms',           'fire',   220),
 ('MARRIAGE',        'Matrimonios',            'Marriages',        ARRAY['N2','N3'], 'CONTRACTABLE', ARRAY['V','C','E','D','S','A','X','O'],     TRUE, 'marriages',          'heart',  230),
 ('CHILD_DEDICATION','Presentación de niños',  'Child dedication', ARRAY['N2','N3'], 'CONTRACTABLE', ARRAY['V','C','E','D','S','A','X','O'],     TRUE, 'child-dedications',  'gift',   240);

-- ---------------------------------------------------------------- membresía
CREATE TABLE membership (
    id               UUID PRIMARY KEY,
    organization_id  UUID         NOT NULL REFERENCES organization (id),
    branch_id        UUID         NOT NULL REFERENCES branch (id),
    person_id        UUID         NOT NULL REFERENCES person (id),
    kind             VARCHAR(10)  NOT NULL DEFAULT 'MEMBER',
    status           VARCHAR(10)  NOT NULL DEFAULT 'PENDING',
    current          BOOLEAN      NOT NULL DEFAULT FALSE,
    start_date       DATE,
    end_date         DATE,
    exit_reason      VARCHAR(15)  NOT NULL DEFAULT 'NONE',
    exit_notes       TEXT,                                        -- cifrado (SecretCipher): solo se lee con la acción H
    approval_id      UUID,
    origin           VARCHAR(10)  NOT NULL DEFAULT 'REQUEST',
    override_reason  VARCHAR(300),
    override_by      UUID,
    requested_by     UUID,
    cancel_reason    VARCHAR(300),
    created_at       TIMESTAMPTZ  NOT NULL,
    created_by       UUID,
    updated_at       TIMESTAMPTZ,
    updated_by       UUID,
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_ms_kind    CHECK (kind IN ('MEMBER','ATTENDEE')),
    CONSTRAINT ck_ms_status  CHECK (status IN ('PENDING','ACTIVE','ENDED','CANCELLED')),
    CONSTRAINT ck_ms_exit    CHECK (exit_reason IN ('NONE','WITHDRAWN','TRANSFERRED','DISCIPLINARY')),
    CONSTRAINT ck_ms_origin  CHECK (origin IN ('REQUEST','RECORD','TRANSFER','VISITOR','PORTAL')),
    CONSTRAINT ck_ms_current CHECK (NOT current OR status = 'ACTIVE'),
    CONSTRAINT ck_ms_dates   CHECK (end_date IS NULL OR start_date IS NULL OR end_date >= start_date),
    CONSTRAINT ck_ms_ended   CHECK (status <> 'ENDED' OR (exit_reason <> 'NONE' AND end_date IS NOT NULL))
);
CREATE UNIQUE INDEX ux_ms_current ON membership (person_id) WHERE current;                                         -- [V3]
CREATE UNIQUE INDEX ux_ms_pending ON membership (person_id) WHERE status = 'PENDING';
CREATE INDEX ix_ms_org_branch ON membership (organization_id, branch_id, status);
CREATE INDEX ix_ms_person ON membership (person_id, start_date DESC);

-- ---------------------------------------------------------------- ritos (bautizo, matrimonio, presentación)
CREATE TABLE rite (
    id                    UUID PRIMARY KEY,
    organization_id       UUID         NOT NULL REFERENCES organization (id),
    branch_id             UUID         NOT NULL REFERENCES branch (id),
    rite_type             VARCHAR(10)  NOT NULL,
    person_id             UUID         NOT NULL REFERENCES person (id),          -- bautizado, cónyuge 1 o niño
    person2_id            UUID         REFERENCES person (id),                    -- cónyuge 2
    event_date            DATE,
    place                 VARCHAR(120),
    officiant_id          UUID         REFERENCES person (id),
    officiant_text        VARCHAR(120),
    external              BOOLEAN      NOT NULL DEFAULT FALSE,                    -- bautizo hecho en otra iglesia
    external_church       VARCHAR(120),
    guardian_consent_by   UUID         REFERENCES person (id),
    civil_record_no       VARCHAR(40),
    spouse2_confirmed_at  TIMESTAMPTZ,
    spouse2_confirmed_by  UUID,
    status                VARCHAR(10)  NOT NULL DEFAULT 'REQUESTED',
    approval_id           UUID,
    override_reason       VARCHAR(300),
    override_by           UUID,
    origin                VARCHAR(10)  NOT NULL DEFAULT 'REQUEST',
    requested_by          UUID,
    cancel_reason         VARCHAR(300),
    completed_at          TIMESTAMPTZ,
    certificate_no        VARCHAR(20),
    created_at            TIMESTAMPTZ  NOT NULL,
    created_by            UUID,
    updated_at            TIMESTAMPTZ,
    updated_by            UUID,
    version               BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_rite_type    CHECK (rite_type IN ('BAPTISM','MARRIAGE','DEDICATION')),
    CONSTRAINT ck_rite_status  CHECK (status IN ('REQUESTED','APPROVED','SCHEDULED','COMPLETED','CANCELLED')),
    CONSTRAINT ck_rite_origin  CHECK (origin IN ('REQUEST','RECORD','PORTAL')),
    CONSTRAINT ck_rite_spouses CHECK (person2_id IS NULL OR person2_id <> person_id),
    CONSTRAINT ck_rite_marriage CHECK ((rite_type = 'MARRIAGE') = (person2_id IS NOT NULL))
);
CREATE UNIQUE INDEX ux_rite_baptism_done ON rite (person_id) WHERE rite_type = 'BAPTISM' AND status = 'COMPLETED';    -- [V5]
CREATE UNIQUE INDEX ux_rite_open ON rite (rite_type, person_id) WHERE status IN ('REQUESTED','APPROVED','SCHEDULED'); -- [V15]
CREATE INDEX ix_rite_org_branch ON rite (organization_id, branch_id, rite_type, status);
CREATE INDEX ix_rite_person ON rite (person_id);
CREATE INDEX ix_rite_person2 ON rite (person2_id) WHERE person2_id IS NOT NULL;

CREATE TABLE rite_guardian (
    rite_id    UUID NOT NULL REFERENCES rite (id) ON DELETE CASCADE,
    person_id  UUID NOT NULL REFERENCES person (id),
    PRIMARY KEY (rite_id, person_id)
);
CREATE INDEX ix_rite_guardian_person ON rite_guardian (person_id);

-- ---------------------------------------------------------------- requisitos y reglas por organización
CREATE TABLE rite_requirement (
    id               UUID PRIMARY KEY,
    organization_id  UUID         NOT NULL REFERENCES organization (id),
    rite_type        VARCHAR(12)  NOT NULL,
    code             VARCHAR(30)  NOT NULL,
    label            VARCHAR(120) NOT NULL,
    required         BOOLEAN      NOT NULL DEFAULT TRUE,
    source           VARCHAR(10)  NOT NULL DEFAULT 'MANUAL',
    min_age          INT,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order       INT          NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL,
    created_by       UUID,
    updated_at       TIMESTAMPTZ,
    updated_by       UUID,
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_rr_type   CHECK (rite_type IN ('MEMBERSHIP','BAPTISM','MARRIAGE','DEDICATION')),
    CONSTRAINT ck_rr_source CHECK (source IN ('MANUAL','COURSE','AGE')),
    CONSTRAINT ck_rr_age    CHECK (source <> 'AGE' OR (min_age IS NOT NULL AND min_age BETWEEN 1 AND 120)),
    CONSTRAINT ux_rr_code   UNIQUE (organization_id, rite_type, code)                                                  -- [V1]
);

CREATE TABLE rite_requirement_check (
    id              UUID PRIMARY KEY,
    requirement_id  UUID         NOT NULL REFERENCES rite_requirement (id) ON DELETE CASCADE,
    subject_id      UUID         NOT NULL,                                     -- membership.id o rite.id
    met             BOOLEAN      NOT NULL,
    note            VARCHAR(200),
    checked_by      UUID,
    checked_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ux_rrc UNIQUE (subject_id, requirement_id)
);

CREATE TABLE rite_rules (
    organization_id     UUID PRIMARY KEY REFERENCES organization (id),
    marriage_min_age    INT         NOT NULL DEFAULT 18,
    dedication_max_age  INT         NOT NULL DEFAULT 12,
    updated_at          TIMESTAMPTZ,
    updated_by          UUID,
    version             BIGINT      NOT NULL DEFAULT 0
);

-- ---------------------------------------------------------------- certificados (M18 les pondrá plantilla y PDF; aquí número, código y anulación)
CREATE TABLE certificate_counter (
    organization_id  UUID        NOT NULL REFERENCES organization (id),
    rite_type        VARCHAR(10) NOT NULL,
    last_number      INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (organization_id, rite_type)
);

CREATE TABLE certificate (
    id               UUID PRIMARY KEY,
    organization_id  UUID         NOT NULL REFERENCES organization (id),
    branch_id        UUID         NOT NULL REFERENCES branch (id),
    rite_type        VARCHAR(10)  NOT NULL,
    rite_id          UUID         NOT NULL REFERENCES rite (id),
    number           INT          NOT NULL,
    certificate_no   VARCHAR(20)  NOT NULL,
    code             VARCHAR(16)  NOT NULL,
    status           VARCHAR(10)  NOT NULL DEFAULT 'VALID',
    issued_at        TIMESTAMPTZ  NOT NULL,
    issued_by        UUID,
    void_reason      VARCHAR(300),
    voided_at        TIMESTAMPTZ,
    voided_by        UUID,
    snapshot         JSONB        NOT NULL,
    CONSTRAINT ck_cert_status CHECK (status IN ('VALID','VOIDED')),
    CONSTRAINT ux_cert_number UNIQUE (organization_id, rite_type, number),
    CONSTRAINT ux_cert_code   UNIQUE (code)
);
CREATE UNIQUE INDEX ux_cert_valid ON certificate (rite_id) WHERE status = 'VALID';
