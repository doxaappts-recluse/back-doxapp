-- V21 · M11 (parte 1) Ministerios: estructura ministerial por organización, cargos, activación por sede, asignaciones con historial y verificación (screening).
-- Los turnos y la programación de voluntarios (VOLUNTEER_SCHEDULING) llegan en la parte 2. Las solicitudes de ingreso viven en approval_request (M21, tipo MINISTRY_JOIN, subject_type MINISTRY).

INSERT INTO module (code, name_es, name_en, levels, kind, actions, delegable, route, icon, sort_order) VALUES
 ('MINISTRY', 'Ministerios', 'Ministries', ARRAY['N2','N3'], 'BASE', ARRAY['V','C','E','D','S','A','X','Q'], TRUE, 'ministries', 'cluster', 260);

-- ---------------------------------------------------------------- ministerio (estructura de la organización)
CREATE TABLE ministry (
    id                  UUID PRIMARY KEY,
    organization_id     UUID         NOT NULL REFERENCES organization (id),
    name                VARCHAR(80)  NOT NULL,
    description         VARCHAR(500),
    color               VARCHAR(9),
    parent_id           UUID         REFERENCES ministry (id),
    requires_screening  BOOLEAN      NOT NULL DEFAULT FALSE,
    screening_type      VARCHAR(40),                                -- catálogo SCREENING_TYPE; null = cualquier tipo vigente
    adult_only          BOOLEAN      NOT NULL DEFAULT FALSE,
    status              VARCHAR(8)   NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ  NOT NULL,
    created_by          UUID,
    updated_at          TIMESTAMPTZ,
    updated_by          UUID,
    version             BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_min_status CHECK (status IN ('ACTIVE','INACTIVE'))
);
CREATE UNIQUE INDEX ux_ministry_name ON ministry (organization_id, lower(name));
CREATE INDEX ix_ministry_parent ON ministry (parent_id);

CREATE TABLE ministry_position (
    id           UUID PRIMARY KEY,
    ministry_id  UUID         NOT NULL REFERENCES ministry (id),
    name         VARCHAR(60)  NOT NULL,
    is_leader    BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order   INT          NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ  NOT NULL
);
CREATE UNIQUE INDEX ux_mposition_name ON ministry_position (ministry_id, lower(name));

-- ---------------------------------------------------------------- ministerio en una sede
CREATE TABLE branch_ministry (
    id                UUID PRIMARY KEY,
    organization_id   UUID         NOT NULL REFERENCES organization (id),
    ministry_id       UUID         NOT NULL REFERENCES ministry (id),
    branch_id         UUID         NOT NULL REFERENCES branch (id),
    leader_person_id  UUID         REFERENCES person (id),
    status            VARCHAR(8)   NOT NULL DEFAULT 'ACTIVE',
    created_at        TIMESTAMPTZ  NOT NULL,
    created_by        UUID,
    updated_at        TIMESTAMPTZ,
    updated_by        UUID,
    version           BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_bmin_status CHECK (status IN ('ACTIVE','INACTIVE')),
    CONSTRAINT ux_bmin UNIQUE (ministry_id, branch_id)
);
CREATE INDEX ix_bmin_branch ON branch_ministry (branch_id, status);
CREATE INDEX ix_bmin_leader ON branch_ministry (leader_person_id);

-- ---------------------------------------------------------------- asignaciones (historial)
CREATE TABLE ministry_assignment (
    id                  UUID PRIMARY KEY,
    organization_id     UUID         NOT NULL REFERENCES organization (id),
    branch_ministry_id  UUID         NOT NULL REFERENCES branch_ministry (id),
    person_id           UUID         NOT NULL REFERENCES person (id),
    position_id         UUID         NOT NULL REFERENCES ministry_position (id),
    from_date           DATE         NOT NULL,
    to_date             DATE,
    status              VARCHAR(6)   NOT NULL DEFAULT 'ACTIVE',
    end_reason          VARCHAR(200),
    created_at          TIMESTAMPTZ  NOT NULL,
    created_by          UUID,
    CONSTRAINT ck_massign_status CHECK (status IN ('ACTIVE','ENDED')),
    CONSTRAINT ck_massign_dates  CHECK (to_date IS NULL OR to_date >= from_date),
    CONSTRAINT ck_massign_ended  CHECK (status = 'ACTIVE' OR to_date IS NOT NULL)
);
CREATE UNIQUE INDEX ux_massign_active ON ministry_assignment (branch_ministry_id, person_id, position_id) WHERE status = 'ACTIVE';
CREATE INDEX ix_massign_person ON ministry_assignment (person_id, status);

-- ---------------------------------------------------------------- verificación de antecedentes (screening)
CREATE TABLE person_screening (
    id               UUID PRIMARY KEY,
    organization_id  UUID         NOT NULL REFERENCES organization (id),
    person_id        UUID         NOT NULL REFERENCES person (id),
    type             VARCHAR(40)  NOT NULL,                     -- catálogo SCREENING_TYPE
    status           VARCHAR(8)   NOT NULL DEFAULT 'PENDING',
    issued_at        DATE,
    expires_at       DATE,
    doc_ref          VARCHAR(120),
    notes            VARCHAR(500),
    created_at       TIMESTAMPTZ  NOT NULL,
    created_by       UUID,
    updated_at       TIMESTAMPTZ,
    updated_by       UUID,
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_pscr_status CHECK (status IN ('NONE','PENDING','CLEARED','EXPIRED')),
    CONSTRAINT ck_pscr_dates  CHECK (expires_at IS NULL OR issued_at IS NULL OR expires_at > issued_at),
    CONSTRAINT ux_pscr UNIQUE (person_id, type)
);
CREATE INDEX ix_pscr_org ON person_screening (organization_id, status);

-- ---------------------------------------------------------------- catálogo: tipos de verificación
INSERT INTO catalog_item (id, organization_id, type, code, name_es, name_en, sort_order, active, source, parent_id, created_at) VALUES
 (gen_random_uuid(), NULL, 'SCREENING_TYPE', 'BACKGROUND',   'Antecedentes',                 'Background check',   10, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'SCREENING_TYPE', 'CHILD_SAFETY', 'Protección de menores',        'Child safety',       20, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'SCREENING_TYPE', 'REFERENCES',   'Referencias pastorales',       'Pastoral references',30, TRUE, 'BASE', NULL, now());
