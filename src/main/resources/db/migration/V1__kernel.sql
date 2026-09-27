-- V1 · Núcleo mínimo de tenancy (M02 organización, M04 sede, M06 persona, M03 catálogo/contrato).
-- Solo lo necesario para autenticación y autorización (F0). Cada módulo amplía sus tablas en migraciones propias.

CREATE TABLE organization (
    id                        UUID PRIMARY KEY,
    name                      VARCHAR(120) NOT NULL,
    legal_name                VARCHAR(160),
    tax_id                    VARCHAR(11),
    slug                      VARCHAR(30)  NOT NULL,
    country                   VARCHAR(2)      NOT NULL DEFAULT 'PE',
    email                     VARCHAR(160),
    phone                     VARCHAR(20),
    founded_date              DATE,
    timezone                  VARCHAR(50)  NOT NULL DEFAULT 'America/Lima',
    currency                  VARCHAR(3)      NOT NULL DEFAULT 'PEN',
    default_language          VARCHAR(2)   NOT NULL DEFAULT 'es',
    fiscal_year_start_month   SMALLINT     NOT NULL DEFAULT 1,
    status                    VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    trial_ends_at             TIMESTAMPTZ,
    created_at                TIMESTAMPTZ  NOT NULL,
    created_by                UUID,
    updated_at                TIMESTAMPTZ,
    updated_by                UUID,
    version                   BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_org_status CHECK (status IN ('DRAFT','TRIAL','ACTIVE','SUSPENDED','CLOSED')),
    CONSTRAINT ck_org_slug   CHECK (slug ~ '^[a-z0-9-]{3,30}$'),
    CONSTRAINT ck_org_lang   CHECK (default_language IN ('es','en')),
    CONSTRAINT ck_org_fy     CHECK (fiscal_year_start_month BETWEEN 1 AND 12)
);
CREATE UNIQUE INDEX ux_organization_slug   ON organization (lower(slug));
CREATE UNIQUE INDEX ux_organization_tax_id ON organization (tax_id) WHERE tax_id IS NOT NULL;

CREATE TABLE branch (
    id               UUID PRIMARY KEY,
    organization_id  UUID         NOT NULL REFERENCES organization (id),
    name             VARCHAR(100) NOT NULL,
    code             VARCHAR(10)  NOT NULL,
    is_main          BOOLEAN      NOT NULL DEFAULT FALSE,
    status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    status_reason    VARCHAR(255),
    created_at       TIMESTAMPTZ  NOT NULL,
    created_by       UUID,
    updated_at       TIMESTAMPTZ,
    updated_by       UUID,
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_branch_status CHECK (status IN ('ACTIVE','INACTIVE'))
);
CREATE UNIQUE INDEX ux_branch_org_name ON branch (organization_id, lower(name));
CREATE UNIQUE INDEX ux_branch_org_code ON branch (organization_id, code);
CREATE UNIQUE INDEX ux_branch_one_main ON branch (organization_id) WHERE is_main AND status = 'ACTIVE';

CREATE TABLE person (
    id               UUID PRIMARY KEY,
    organization_id  UUID         NOT NULL REFERENCES organization (id),
    doc_type         VARCHAR(10)  NOT NULL,
    doc_number       VARCHAR(12)  NOT NULL,
    first_name       VARCHAR(80)  NOT NULL,
    last_name        VARCHAR(80)  NOT NULL,
    email            VARCHAR(160),
    phone            VARCHAR(20),
    status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMPTZ  NOT NULL,
    created_by       UUID,
    updated_at       TIMESTAMPTZ,
    updated_by       UUID,
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_person_doc_type CHECK (doc_type IN ('DNI','CE','PASSPORT','RUC')),
    CONSTRAINT ck_person_status   CHECK (status IN ('ACTIVE','INACTIVE','DECEASED','MERGED'))
);
CREATE UNIQUE INDEX ux_person_org_doc ON person (organization_id, doc_type, doc_number);
CREATE INDEX ix_person_org_email      ON person (organization_id, lower(email));

-- Catálogo de módulos (M03). El código es inmutable y es la clave de permisos.
CREATE TABLE module (
    code         VARCHAR(40) PRIMARY KEY,
    name_es      VARCHAR(100) NOT NULL,
    name_en      VARCHAR(100) NOT NULL,
    levels       TEXT[]       NOT NULL,
    parent_code  VARCHAR(40) REFERENCES module (code),
    kind         VARCHAR(20)  NOT NULL,
    actions      TEXT[]       NOT NULL,
    delegable    BOOLEAN      NOT NULL DEFAULT TRUE,
    route        VARCHAR(120),
    icon         VARCHAR(60),
    sort_order   INT          NOT NULL DEFAULT 0,
    status       VARCHAR(20)  NOT NULL DEFAULT 'PUBLISHED',
    CONSTRAINT ck_module_code   CHECK (code ~ '^[A-Z0-9_]{3,40}$'),
    CONSTRAINT ck_module_kind   CHECK (kind IN ('BASE','CONTRACTABLE')),
    CONSTRAINT ck_module_status CHECK (status IN ('DRAFT','PUBLISHED','RETIRED'))
);

-- Contrato (M03): en F0 solo lo que necesita el gate de contrato y las licencias.
CREATE TABLE contract (
    id                 UUID PRIMARY KEY,
    organization_id    UUID        NOT NULL REFERENCES organization (id),
    branch_id          UUID        REFERENCES branch (id),
    status             VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    start_date         DATE        NOT NULL,
    end_date           DATE        NOT NULL,
    max_licenses       INT         NOT NULL,
    distribution_mode  VARCHAR(20) NOT NULL DEFAULT 'SHARED',
    scope              VARCHAR(20) NOT NULL DEFAULT 'ORGANIZATION',
    created_at         TIMESTAMPTZ NOT NULL,
    created_by         UUID,
    updated_at         TIMESTAMPTZ,
    updated_by         UUID,
    version            BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_contract_status CHECK (status IN ('PENDING','ACTIVE','SUSPENDED','EXPIRED','CANCELLED','REPLACED')),
    CONSTRAINT ck_contract_dist   CHECK (distribution_mode IN ('SHARED','ALLOCATED')),
    CONSTRAINT ck_contract_scope  CHECK (scope IN ('ORGANIZATION','BRANCH')),
    CONSTRAINT ck_contract_dates  CHECK (end_date > start_date),
    CONSTRAINT ck_contract_lic    CHECK (max_licenses > 0)
);
CREATE INDEX ix_contract_org_status ON contract (organization_id, status);

CREATE TABLE contract_module (
    contract_id  UUID        NOT NULL REFERENCES contract (id),
    module_code  VARCHAR(40) NOT NULL REFERENCES module (code),
    PRIMARY KEY (contract_id, module_code)
);

CREATE TABLE contract_branch_license (
    id                  UUID PRIMARY KEY,
    contract_id         UUID NOT NULL REFERENCES contract (id),
    branch_id           UUID NOT NULL REFERENCES branch (id),
    allocated_licenses  INT  NOT NULL,
    CONSTRAINT ck_cbl_alloc CHECK (allocated_licenses >= 0),
    CONSTRAINT ux_cbl UNIQUE (contract_id, branch_id)
);
