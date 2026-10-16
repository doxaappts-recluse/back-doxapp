-- V20 · M10 Grupos y células: grupo, integrantes con rol, reuniones con asistencia (núcleo M09, contexto GROUP_MEETING) y reglas por organización.
-- Las solicitudes de ingreso viven en approval_request (M21, tipo GROUP_JOIN, subject_type GROUP).

INSERT INTO module (code, name_es, name_en, levels, kind, actions, delegable, route, icon, sort_order) VALUES
 ('SMALL_GROUP', 'Grupos y células', 'Small groups', ARRAY['N2','N3'], 'CONTRACTABLE', ARRAY['V','C','E','D','S','A','X'], TRUE, 'groups', 'team', 250);

-- ---------------------------------------------------------------- grupo
CREATE TABLE small_group (
    id               UUID PRIMARY KEY,
    organization_id  UUID         NOT NULL REFERENCES organization (id),
    branch_id        UUID         NOT NULL REFERENCES branch (id),
    name             VARCHAR(80)  NOT NULL,
    category         VARCHAR(40),                               -- catálogo GROUP_CATEGORY
    audience         VARCHAR(6)   NOT NULL DEFAULT 'ADULT',
    description      VARCHAR(500),
    host_person_id   UUID         REFERENCES person (id),       -- anfitrión (quien recibe en su casa)
    meeting_day      SMALLINT,                                  -- 1 = lunes … 7 = domingo (ISO)
    meeting_time     TIME,
    location         VARCHAR(160),                              -- ubicación exacta: solo la ven los integrantes y el personal [V12]
    zone             VARCHAR(80),                               -- zona que sí se publica
    capacity         INT,
    open_to_join     BOOLEAN      NOT NULL DEFAULT FALSE,
    start_date       DATE,
    end_date         DATE,
    parent_group_id  UUID         REFERENCES small_group (id),
    status           VARCHAR(8)   NOT NULL DEFAULT 'DRAFT',
    status_reason    VARCHAR(255),
    closed_at        TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL,
    created_by       UUID,
    updated_at       TIMESTAMPTZ,
    updated_by       UUID,
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_sg_audience CHECK (audience IN ('ADULT','YOUTH','MINOR','MIXED')),
    CONSTRAINT ck_sg_status   CHECK (status IN ('DRAFT','ACTIVE','PAUSED','CLOSED')),
    CONSTRAINT ck_sg_day      CHECK (meeting_day IS NULL OR meeting_day BETWEEN 1 AND 7),
    CONSTRAINT ck_sg_capacity CHECK (capacity IS NULL OR capacity >= 1),
    CONSTRAINT ck_sg_dates    CHECK (end_date IS NULL OR start_date IS NULL OR end_date >= start_date)
);
CREATE UNIQUE INDEX ux_sg_name ON small_group (branch_id, lower(name));
CREATE INDEX ix_sg_org ON small_group (organization_id, status);
CREATE INDEX ix_sg_parent ON small_group (parent_group_id);

-- ---------------------------------------------------------------- integrantes
CREATE TABLE group_member (
    id               UUID PRIMARY KEY,
    organization_id  UUID         NOT NULL REFERENCES organization (id),
    group_id         UUID         NOT NULL REFERENCES small_group (id),
    person_id        UUID         NOT NULL REFERENCES person (id),
    role             VARCHAR(8)   NOT NULL DEFAULT 'MEMBER',
    status           VARCHAR(6)   NOT NULL DEFAULT 'ACTIVE',
    joined_at        DATE         NOT NULL,
    left_at          DATE,
    left_reason      VARCHAR(10),                                -- LEFT | REMOVED | TRANSFER | MULTIPLIED | CLOSED
    created_at       TIMESTAMPTZ  NOT NULL,
    created_by       UUID,
    CONSTRAINT ck_gm_role   CHECK (role IN ('LEADER','COLEADER','INTERN','MEMBER')),
    CONSTRAINT ck_gm_status CHECK (status IN ('ACTIVE','LEFT')),
    CONSTRAINT ck_gm_left   CHECK (status = 'ACTIVE' OR left_at IS NOT NULL)
);
CREATE UNIQUE INDEX ux_gm_active ON group_member (group_id, person_id) WHERE status = 'ACTIVE';                  -- [V6]
CREATE UNIQUE INDEX ux_gm_leader ON group_member (group_id) WHERE status = 'ACTIVE' AND role = 'LEADER';          -- un solo líder
CREATE INDEX ix_gm_person ON group_member (person_id, status);

-- ---------------------------------------------------------------- reuniones
CREATE TABLE group_meeting (
    id                     UUID PRIMARY KEY,
    organization_id        UUID         NOT NULL REFERENCES organization (id),
    branch_id              UUID         NOT NULL REFERENCES branch (id),
    group_id               UUID         NOT NULL REFERENCES small_group (id),
    meeting_date           DATE         NOT NULL,
    meeting_time           TIME,
    topic                  VARCHAR(160),
    location               VARCHAR(160),
    status                 VARCHAR(9)   NOT NULL DEFAULT 'PLANNED',
    notes                  VARCHAR(1000),
    no_attendance_reason   VARCHAR(200),
    cancel_reason          VARCHAR(200),
    attendance_session_id  UUID         REFERENCES attendance_session (id),
    series_id              UUID,
    held_at                TIMESTAMPTZ,
    created_at             TIMESTAMPTZ  NOT NULL,
    created_by             UUID,
    updated_at             TIMESTAMPTZ,
    updated_by             UUID,
    version                BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_gmt_status CHECK (status IN ('PLANNED','HELD','CANCELLED'))
);
CREATE UNIQUE INDEX ux_gmt_day ON group_meeting (group_id, meeting_date) WHERE status <> 'CANCELLED';
CREATE INDEX ix_gmt_group ON group_meeting (group_id, meeting_date);

-- ---------------------------------------------------------------- reglas por organización
CREATE TABLE group_rules (
    organization_id             UUID PRIMARY KEY REFERENCES organization (id),
    leader_requires_membership  BOOLEAN NOT NULL DEFAULT TRUE,
    allow_multiple_groups       BOOLEAN NOT NULL DEFAULT TRUE,
    min_adult_leaders_minors    INT     NOT NULL DEFAULT 2,
    updated_at                  TIMESTAMPTZ,
    updated_by                  UUID,
    version                     BIGINT  NOT NULL DEFAULT 0,
    CONSTRAINT ck_gr_min CHECK (min_adult_leaders_minors BETWEEN 1 AND 10)
);

-- ---------------------------------------------------------------- catálogo: categorías de grupo
INSERT INTO catalog_item (id, organization_id, type, code, name_es, name_en, sort_order, active, source, parent_id, created_at) VALUES
 (gen_random_uuid(), NULL, 'GROUP_CATEGORY', 'CELL',     'Célula de hogar',     'Home cell',         10, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'GROUP_CATEGORY', 'YOUTH',    'Jóvenes',             'Youth',             20, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'GROUP_CATEGORY', 'FAMILIES', 'Familias',            'Families',          30, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'GROUP_CATEGORY', 'WOMEN',    'Mujeres',             'Women',             40, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'GROUP_CATEGORY', 'MEN',      'Varones',             'Men',               50, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'GROUP_CATEGORY', 'STUDY',    'Estudio bíblico',     'Bible study',       60, TRUE, 'BASE', NULL, now());
