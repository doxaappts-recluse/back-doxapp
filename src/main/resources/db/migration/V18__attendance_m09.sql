-- V18 · M09 Asistencia y check-in: cultos (plantillas), sesiones y registros de asistencia (núcleo compartido), QR, check-in de niños y alertas de inasistencia.
-- Los grupos (M10), eventos (M14), clases (M13) y turnos (M11) reutilizarán attendance_session / attendance_record con otro context_type.

INSERT INTO module (code, name_es, name_en, levels, kind, actions, delegable, route, icon, sort_order) VALUES
 ('ATTENDANCE',    'Asistencia',        'Attendance',     ARRAY['N2','N3'], 'BASE',         ARRAY['V','C','E','D','S','A','X','T'], TRUE, 'attendance',    'check-circle', 190),
 ('CHILD_CHECKIN', 'Check-in de niños', 'Child check-in', ARRAY['N2','N3'], 'CONTRACTABLE', ARRAY['V','C','E','T','H'],             TRUE, 'child-checkin', 'smile',        200);

-- ---------------------------------------------------------------- cultos (plantillas)
CREATE TABLE church_service (
    id                    UUID PRIMARY KEY,
    organization_id       UUID         NOT NULL REFERENCES organization (id),
    branch_id             UUID         NOT NULL REFERENCES branch (id),
    name                  VARCHAR(80)  NOT NULL,
    service_type          VARCHAR(40),                         -- catálogo SERVICE_TYPE
    day_of_week           SMALLINT,                            -- 1 = lunes … 7 = domingo (ISO)
    start_time            TIME         NOT NULL,
    duration_min          INT          NOT NULL,
    space_note            VARCHAR(80),                         -- texto libre hasta que exista M16 (espacios)
    recurrence            VARCHAR(10)  NOT NULL DEFAULT 'WEEKLY',
    self_checkin_enabled  BOOLEAN      NOT NULL DEFAULT FALSE,
    status                VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    created_at            TIMESTAMPTZ  NOT NULL,
    created_by            UUID,
    updated_at            TIMESTAMPTZ,
    updated_by            UUID,
    version               BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_cs_dow        CHECK (day_of_week IS NULL OR day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_cs_recurrence CHECK (recurrence IN ('WEEKLY','NONE')),
    CONSTRAINT ck_cs_weekly     CHECK (recurrence <> 'WEEKLY' OR day_of_week IS NOT NULL),
    CONSTRAINT ck_cs_duration   CHECK (duration_min BETWEEN 15 AND 480),
    CONSTRAINT ck_cs_status     CHECK (status IN ('ACTIVE','INACTIVE'))
);
CREATE UNIQUE INDEX ux_cs_branch_name ON church_service (branch_id, lower(name));
CREATE INDEX ix_cs_org ON church_service (organization_id, status);

-- ---------------------------------------------------------------- sesiones y registros (núcleo 01 §2)
CREATE TABLE attendance_session (
    id                UUID PRIMARY KEY,
    organization_id   UUID         NOT NULL REFERENCES organization (id),
    branch_id         UUID         NOT NULL REFERENCES branch (id),
    context_type      VARCHAR(15)  NOT NULL DEFAULT 'SERVICE',
    context_id        UUID,
    title             VARCHAR(120) NOT NULL,
    session_date      DATE         NOT NULL,
    starts_at         TIMESTAMPTZ  NOT NULL,
    ends_at           TIMESTAMPTZ  NOT NULL,
    status            VARCHAR(10)  NOT NULL DEFAULT 'PLANNED',
    anonymous_count   INT          NOT NULL DEFAULT 0,
    origin            VARCHAR(10)  NOT NULL DEFAULT 'MANUAL',
    self_checkin      BOOLEAN      NOT NULL DEFAULT FALSE,
    opened_at         TIMESTAMPTZ,
    opened_by         UUID,
    closed_at         TIMESTAMPTZ,
    closed_by         UUID,
    reopen_count      INT          NOT NULL DEFAULT 0,
    last_reopen_at    TIMESTAMPTZ,
    last_reopen_reason VARCHAR(255),
    created_at        TIMESTAMPTZ  NOT NULL,
    created_by        UUID,
    version           BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_as_context CHECK (context_type IN ('SERVICE','EVENT','GROUP_MEETING','CLASS','SHIFT')),
    CONSTRAINT ck_as_status  CHECK (status IN ('PLANNED','OPEN','CLOSED')),
    CONSTRAINT ck_as_origin  CHECK (origin IN ('JOB','MANUAL')),
    CONSTRAINT ck_as_anon    CHECK (anonymous_count >= 0),
    CONSTRAINT ck_as_range   CHECK (ends_at > starts_at)
);
CREATE UNIQUE INDEX ux_as_context_date ON attendance_session (context_type, context_id, session_date) WHERE context_id IS NOT NULL;
CREATE INDEX ix_as_org_branch_date ON attendance_session (organization_id, branch_id, session_date DESC);
CREATE INDEX ix_as_open ON attendance_session (status) WHERE status <> 'CLOSED';

CREATE TABLE attendance_record (
    id                UUID PRIMARY KEY,
    session_id        UUID         NOT NULL REFERENCES attendance_session (id),
    organization_id   UUID         NOT NULL REFERENCES organization (id),
    branch_id         UUID         NOT NULL REFERENCES branch (id),
    person_id         UUID         NOT NULL REFERENCES person (id),
    status            VARCHAR(10)  NOT NULL DEFAULT 'PRESENT',
    method            VARCHAR(10)  NOT NULL DEFAULT 'MANUAL',
    at                TIMESTAMPTZ  NOT NULL,
    recorded_by       UUID,
    CONSTRAINT ck_ar_status CHECK (status IN ('PRESENT','ABSENT','EXCUSED','LATE')),
    CONSTRAINT ck_ar_method CHECK (method IN ('MANUAL','LIST','QR','SELF')),
    CONSTRAINT ux_ar_session_person UNIQUE (session_id, person_id)
);
CREATE INDEX ix_ar_person ON attendance_record (person_id, at DESC);
CREATE INDEX ix_ar_branch ON attendance_record (organization_id, branch_id, at DESC);

-- Un código QR se usa una sola vez (jti); solo se guarda su identificador, nunca el código.
CREATE TABLE attendance_qr_use (
    jti          VARCHAR(40) PRIMARY KEY,
    session_id   UUID        NOT NULL REFERENCES attendance_session (id),
    person_id    UUID        NOT NULL REFERENCES person (id),
    used_at      TIMESTAMPTZ NOT NULL
);

-- ---------------------------------------------------------------- reglas por organización
CREATE TABLE attendance_rules (
    organization_id      UUID PRIMARY KEY REFERENCES organization (id),
    absence_weeks_alert  INT         NOT NULL DEFAULT 4,
    child_max_age        INT         NOT NULL DEFAULT 12,
    qr_ttl_seconds       INT         NOT NULL DEFAULT 30,
    reopen_days          INT         NOT NULL DEFAULT 7,
    updated_at           TIMESTAMPTZ,
    updated_by           UUID,
    version              BIGINT      NOT NULL DEFAULT 0
);

-- ---------------------------------------------------------------- alertas de inasistencia (una por racha; M12 las tomará como tarea)
CREATE TABLE attendance_absence_alert (
    id               UUID PRIMARY KEY,
    organization_id  UUID        NOT NULL REFERENCES organization (id),
    branch_id        UUID        NOT NULL REFERENCES branch (id),
    person_id        UUID        NOT NULL REFERENCES person (id),
    last_attended    DATE        NOT NULL,
    weeks            INT         NOT NULL,
    status           VARCHAR(10) NOT NULL DEFAULT 'OPEN',
    created_at       TIMESTAMPTZ NOT NULL,
    resolved_at      TIMESTAMPTZ,
    CONSTRAINT ck_aaa_status CHECK (status IN ('OPEN','RESOLVED'))
);
CREATE UNIQUE INDEX ux_aaa_open ON attendance_absence_alert (person_id) WHERE status = 'OPEN';
CREATE INDEX ix_aaa_org ON attendance_absence_alert (organization_id, status, created_at DESC);

-- ---------------------------------------------------------------- check-in de niños
CREATE TABLE child_checkin (
    id                UUID PRIMARY KEY,
    organization_id   UUID         NOT NULL REFERENCES organization (id),
    branch_id         UUID         NOT NULL REFERENCES branch (id),
    session_id        UUID         NOT NULL REFERENCES attendance_session (id),
    child_id          UUID         NOT NULL REFERENCES person (id),
    guardian_id       UUID         NOT NULL REFERENCES person (id),
    room              VARCHAR(60),
    tag_code          VARCHAR(6)   NOT NULL,
    allergy_snapshot  TEXT,                                     -- cifrado, igual que Person.allergies
    checked_in_at     TIMESTAMPTZ  NOT NULL,
    checked_in_by     UUID,
    checked_out_at    TIMESTAMPTZ,
    checked_out_by    UUID,
    picked_up_by      UUID         REFERENCES person (id),
    pickup_method     VARCHAR(10),
    CONSTRAINT ck_cc_pickup CHECK (pickup_method IS NULL OR pickup_method IN ('CODE','GUARDIAN')),
    CONSTRAINT ux_cc_tag   UNIQUE (session_id, tag_code),
    CONSTRAINT ux_cc_child UNIQUE (session_id, child_id)
);
CREATE INDEX ix_cc_session ON child_checkin (session_id, checked_out_at);

-- ---------------------------------------------------------------- catálogo: tipos de culto
INSERT INTO catalog_item (id, organization_id, type, code, name_es, name_en, sort_order, active, source, parent_id, created_at) VALUES
 (gen_random_uuid(), NULL, 'SERVICE_TYPE', 'SUNDAY',   'Culto dominical',       'Sunday service',     10, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'SERVICE_TYPE', 'MIDWEEK',  'Reunión entre semana',  'Midweek meeting',    20, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'SERVICE_TYPE', 'PRAYER',   'Reunión de oración',    'Prayer meeting',     30, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'SERVICE_TYPE', 'YOUTH',    'Culto de jóvenes',      'Youth service',      40, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'SERVICE_TYPE', 'CHILDREN', 'Escuela dominical',     'Sunday school',      50, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'SERVICE_TYPE', 'SPECIAL',  'Culto especial',        'Special service',    60, TRUE, 'BASE', NULL, now());
