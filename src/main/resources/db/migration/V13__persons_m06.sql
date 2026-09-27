-- V13 · M06 Personas y familias (parte 1): ficha completa, historial de sedes, etiquetas y hogares.
-- Fusión, anonimizado, exportación, importación, consentimientos y portal llegan en la parte 2 / M24.

-- ---------------------------------------------------------------- módulos
INSERT INTO module (code, name_es, name_en, levels, kind, actions, delegable, route, icon, sort_order) VALUES
 ('PERSON',      'Personas',           'People',      ARRAY['N2','N3'], 'BASE', ARRAY['V','C','E','S','X','H','M','I','P'], TRUE, 'persons',    'team', 120),
 ('FAMILY',      'Hogares',            'Households', ARRAY['N2','N3'], 'BASE', ARRAY['V','C','E','D'],                     TRUE, 'households', 'home', 130),
 ('PERSON_TAGS', 'Etiquetas de personas', 'People tags', ARRAY['N2','N3'], 'BASE', ARRAY['V','C','E','D'],                  TRUE, 'tags',       'tags', 140);

-- ---------------------------------------------------------------- ficha de la persona
ALTER TABLE person
    ADD COLUMN sex               VARCHAR(10),
    ADD COLUMN birth_date        DATE,
    ADD COLUMN marital_status    VARCHAR(15),
    ADD COLUMN whatsapp          VARCHAR(20),
    ADD COLUMN address_line      VARCHAR(200),
    ADD COLUMN address_district  VARCHAR(80),
    ADD COLUMN address_city      VARCHAR(80),
    ADD COLUMN address_region    VARCHAR(80),
    ADD COLUMN address_country   VARCHAR(2),
    ADD COLUMN address_reference VARCHAR(200),
    ADD COLUMN occupation        VARCHAR(120),
    ADD COLUMN primary_branch_id UUID REFERENCES branch (id),
    ADD COLUMN joined_at         DATE,
    ADD COLUMN merged_into       UUID REFERENCES person (id),
    ADD COLUMN deceased_at       DATE,
    ADD COLUMN status_reason     VARCHAR(300),
    ADD COLUMN private_notes     TEXT,           -- cifrado (AES-256-GCM); solo lo lee quien tiene la acción H
    ADD COLUMN allergies         TEXT;           -- cifrado (AES-256-GCM); solo lo lee quien tiene la acción H

ALTER TABLE person
    ADD CONSTRAINT ck_person_sex     CHECK (sex IS NULL OR sex IN ('MALE','FEMALE')),
    ADD CONSTRAINT ck_person_marital CHECK (marital_status IS NULL OR marital_status IN ('SINGLE','MARRIED','COMMON_LAW','WIDOWED','DIVORCED','SEPARATED')),
    ADD CONSTRAINT ck_person_birth   CHECK (birth_date IS NULL OR birth_date >= DATE '1900-01-01');

CREATE INDEX ix_person_branch_status ON person (organization_id, primary_branch_id, status);
CREATE INDEX ix_person_name          ON person (organization_id, lower(last_name), lower(first_name));

-- Historial de sedes: una fila por periodo; solo una vigente por persona.
CREATE TABLE person_branch (
    id          UUID PRIMARY KEY,
    person_id   UUID        NOT NULL REFERENCES person (id),
    branch_id   UUID        NOT NULL REFERENCES branch (id),
    from_date   DATE        NOT NULL,
    to_date     DATE,
    is_current BOOLEAN     NOT NULL DEFAULT TRUE,
    reason      VARCHAR(300),
    created_at  TIMESTAMPTZ NOT NULL,
    created_by  UUID,
    CONSTRAINT ck_pb_range CHECK (to_date IS NULL OR to_date >= from_date)
);
CREATE UNIQUE INDEX ux_pb_current ON person_branch (person_id) WHERE is_current;
CREATE INDEX ix_pb_person ON person_branch (person_id, from_date DESC);
CREATE INDEX ix_pb_branch ON person_branch (branch_id) WHERE is_current;

-- ---------------------------------------------------------------- etiquetas
CREATE TABLE tag (
    id               UUID PRIMARY KEY,
    organization_id  UUID        NOT NULL REFERENCES organization (id),
    name             VARCHAR(40) NOT NULL,
    color            VARCHAR(7)  NOT NULL DEFAULT '#1677FF',
    created_at       TIMESTAMPTZ NOT NULL,
    created_by       UUID,
    updated_at       TIMESTAMPTZ,
    updated_by       UUID,
    version          BIGINT      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_tag_org_name ON tag (organization_id, lower(name));

CREATE TABLE person_tag (
    person_id   UUID        NOT NULL REFERENCES person (id),
    tag_id      UUID        NOT NULL REFERENCES tag (id),
    created_at  TIMESTAMPTZ NOT NULL,
    created_by  UUID,
    PRIMARY KEY (person_id, tag_id)
);
CREATE INDEX ix_person_tag_tag ON person_tag (tag_id);

-- ---------------------------------------------------------------- hogares
CREATE TABLE household (
    id                UUID PRIMARY KEY,
    organization_id   UUID         NOT NULL REFERENCES organization (id),
    name              VARCHAR(100) NOT NULL,
    address_line      VARCHAR(200),
    address_district  VARCHAR(80),
    address_city      VARCHAR(80),
    address_region    VARCHAR(80),
    address_country   VARCHAR(2),
    address_reference VARCHAR(200),
    status            VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    created_at        TIMESTAMPTZ  NOT NULL,
    created_by        UUID,
    updated_at        TIMESTAMPTZ,
    updated_by        UUID,
    version           BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_hh_status CHECK (status IN ('ACTIVE','INACTIVE'))
);
CREATE INDEX ix_hh_org ON household (organization_id, status, lower(name));

-- Integrante: activo mientras left_at sea NULL. Una persona en un solo hogar activo [V9]; una sola cabeza activa por hogar [V8].
CREATE TABLE household_member (
    id            UUID PRIMARY KEY,
    household_id  UUID        NOT NULL REFERENCES household (id),
    person_id     UUID        NOT NULL REFERENCES person (id),
    role          VARCHAR(10) NOT NULL,
    guardian      BOOLEAN     NOT NULL DEFAULT FALSE,
    joined_at     DATE        NOT NULL,
    left_at       DATE,
    created_at    TIMESTAMPTZ NOT NULL,
    created_by    UUID,
    CONSTRAINT ck_hm_role  CHECK (role IN ('HEAD','SPOUSE','CHILD','RELATIVE','OTHER')),
    CONSTRAINT ck_hm_range CHECK (left_at IS NULL OR left_at >= joined_at)
);
CREATE UNIQUE INDEX ux_hm_person_active ON household_member (person_id) WHERE left_at IS NULL;
CREATE UNIQUE INDEX ux_hm_head_active   ON household_member (household_id) WHERE role = 'HEAD' AND left_at IS NULL;
CREATE INDEX ix_hm_household ON household_member (household_id) WHERE left_at IS NULL;
