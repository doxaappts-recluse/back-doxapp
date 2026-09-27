-- V24 · M13 Formación (Academia Bíblica): malla curricular de la organización, cursos vinculados por nivel y cursos extra por sede, dictados con docente y
-- horario, matrículas con prerrequisito entre sedes y escala de notas. Certificado propio (training_certificate) hasta que M18 unifique plantillas; aulas de
-- M16 quedan como texto libre en "location". Reusa AttendanceService (núcleo M09, contexto CLASS, una sesión por fecha de clase). Portal (N4: mi formación,
-- oferta abierta, inscribirme/retirarme) queda para M24, igual que en M09/M10/M11a/M11b/M12.

INSERT INTO module (code, name_es, name_en, levels, kind, actions, delegable, route, icon, sort_order) VALUES
 ('TRAINING', 'Formación', 'Training', ARRAY['N2','N3'], 'CONTRACTABLE', ARRAY['V','C','E','D','S','X','O'], TRUE, 'training', 'read', 280);

-- ---------------------------------------------------------------- malla curricular (una ACTIVA por organización)
CREATE TABLE curriculum (
    id               UUID PRIMARY KEY,
    organization_id  UUID         NOT NULL REFERENCES organization (id),
    name             VARCHAR(120) NOT NULL,
    description      VARCHAR(500),
    status           VARCHAR(10)  NOT NULL DEFAULT 'DRAFT',
    created_at       TIMESTAMPTZ  NOT NULL,
    created_by       UUID,
    updated_at       TIMESTAMPTZ,
    updated_by       UUID,
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_curriculum_status CHECK (status IN ('DRAFT', 'ACTIVE', 'RETIRED'))
);
CREATE UNIQUE INDEX ux_curriculum_name ON curriculum (organization_id, lower(name));                        -- [V1]
CREATE UNIQUE INDEX ux_curriculum_active ON curriculum (organization_id) WHERE status = 'ACTIVE';            -- un solo ACTIVE por org

-- ---------------------------------------------------------------- curso: vinculado a una malla (por nivel) o extra de una sede
CREATE TABLE course (
    id                  UUID PRIMARY KEY,
    organization_id     UUID         NOT NULL REFERENCES organization (id),
    curriculum_id       UUID         REFERENCES curriculum (id),
    order_num           INT,
    branch_id           UUID         REFERENCES branch (id),
    name                VARCHAR(120) NOT NULL,
    description         VARCHAR(500),
    hours               INT          NOT NULL DEFAULT 0,
    min_attendance_pct  INT,
    pass_grade          NUMERIC(5, 2),
    status              VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ  NOT NULL,
    created_by          UUID,
    updated_at          TIMESTAMPTZ,
    updated_by          UUID,
    version             BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_course_status CHECK (status IN ('ACTIVE', 'RETIRED')),
    CONSTRAINT ck_course_hours  CHECK (hours >= 0),
    CONSTRAINT ck_course_attpct CHECK (min_attendance_pct IS NULL OR (min_attendance_pct BETWEEN 0 AND 100)),
    CONSTRAINT ck_course_kind   CHECK ((curriculum_id IS NOT NULL AND order_num IS NOT NULL AND branch_id IS NULL)
                                    OR (curriculum_id IS NULL AND order_num IS NULL AND branch_id IS NOT NULL))
);
CREATE UNIQUE INDEX ux_course_order ON course (curriculum_id, order_num) WHERE curriculum_id IS NOT NULL;    -- [V2]
CREATE INDEX ix_course_branch ON course (branch_id) WHERE branch_id IS NOT NULL;

-- ---------------------------------------------------------------- dictado (course_class): un curso ofrecido en una sede con docente y horario
CREATE TABLE course_class (
    id                  UUID PRIMARY KEY,
    organization_id     UUID         NOT NULL REFERENCES organization (id),
    branch_id           UUID         NOT NULL REFERENCES branch (id),
    course_id           UUID         NOT NULL REFERENCES course (id),
    teacher_person_id   UUID         NOT NULL REFERENCES person (id),
    day_of_week         INT,                                                     -- 1=lunes .. 7=domingo; null = sin patrón semanal fijo
    start_time          TIME,
    end_time            TIME,
    location            VARCHAR(160),                                            -- aula libre hasta M16
    start_date          DATE         NOT NULL,
    end_date            DATE         NOT NULL,
    capacity            INT          NOT NULL DEFAULT 20,
    status              VARCHAR(12)  NOT NULL DEFAULT 'PLANNED',
    cancel_reason       VARCHAR(300),
    started_at          TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL,
    created_by          UUID,
    updated_at          TIMESTAMPTZ,
    updated_by          UUID,
    version             BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_cclass_status CHECK (status IN ('PLANNED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_cclass_day    CHECK (day_of_week IS NULL OR (day_of_week BETWEEN 1 AND 7)),
    CONSTRAINT ck_cclass_dates  CHECK (end_date >= start_date),
    CONSTRAINT ck_cclass_time   CHECK (start_time IS NULL OR end_time IS NULL OR end_time > start_time),
    CONSTRAINT ck_cclass_cap    CHECK (capacity >= 1)
);
CREATE INDEX ix_cclass_branch ON course_class (branch_id, status);
CREATE INDEX ix_cclass_course ON course_class (course_id);
CREATE INDEX ix_cclass_teacher ON course_class (teacher_person_id);

-- ---------------------------------------------------------------- matrícula
CREATE TABLE enrollment (
    id                UUID PRIMARY KEY,
    organization_id   UUID         NOT NULL REFERENCES organization (id),
    branch_id         UUID         NOT NULL REFERENCES branch (id),
    person_id         UUID         NOT NULL REFERENCES person (id),
    class_id          UUID         NOT NULL REFERENCES course_class (id),
    status            VARCHAR(10)  NOT NULL DEFAULT 'ENROLLED',
    final_grade       NUMERIC(5, 2),
    status_reason     VARCHAR(300),
    override_reason   VARCHAR(300),
    overridden_by     UUID,
    enrolled_at       TIMESTAMPTZ  NOT NULL,
    resolved_at       TIMESTAMPTZ,
    graded_at         TIMESTAMPTZ,
    created_by        UUID,
    version           BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_enrollment_status CHECK (status IN ('ENROLLED', 'APPROVED', 'FAILED', 'WITHDRAWN')),
    CONSTRAINT ux_enrollment UNIQUE (person_id, class_id)                                                    -- [V7]
);
CREATE INDEX ix_enrollment_person ON enrollment (person_id);
CREATE INDEX ix_enrollment_class ON enrollment (class_id, status);

-- ---------------------------------------------------------------- escala de notas por organización (singleton)
CREATE TABLE training_grade_scale (
    organization_id  UUID PRIMARY KEY REFERENCES organization (id),
    grade_min        NUMERIC(5, 2) NOT NULL DEFAULT 0,
    grade_max        NUMERIC(5, 2) NOT NULL DEFAULT 20,
    grade_pass       NUMERIC(5, 2) NOT NULL DEFAULT 11,
    updated_at       TIMESTAMPTZ,
    updated_by       UUID,
    version          BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_gscale_order CHECK (grade_min < grade_pass AND grade_pass <= grade_max)                    -- [V5]
);

-- ---------------------------------------------------------------- certificado de formación (propio hasta que M18 unifique plantillas)
CREATE TABLE training_certificate_counter (
    organization_id  UUID PRIMARY KEY REFERENCES organization (id),
    last_number      INT NOT NULL DEFAULT 0
);
CREATE TABLE training_certificate (
    id               UUID PRIMARY KEY,
    organization_id  UUID         NOT NULL REFERENCES organization (id),
    branch_id        UUID         NOT NULL REFERENCES branch (id),
    enrollment_id    UUID         NOT NULL REFERENCES enrollment (id),
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
    CONSTRAINT ck_tcert_status CHECK (status IN ('VALID', 'VOIDED')),
    CONSTRAINT ux_tcert_number UNIQUE (organization_id, number),
    CONSTRAINT ux_tcert_code   UNIQUE (code)
);
CREATE INDEX ix_tcert_enrollment ON training_certificate (enrollment_id);
