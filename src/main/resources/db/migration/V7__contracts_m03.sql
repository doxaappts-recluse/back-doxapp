-- V7 · M03 Módulos, planes y contratos.
-- Agrega planes, amplía el contrato (plan, precio, versionado, marcas de tiempo), el registro de avisos de vencimiento
-- y los módulos del catálogo que gestiona este módulo.

-- ---------------------------------------------------------------- planes
CREATE TABLE plan (
    id           UUID PRIMARY KEY,
    code         VARCHAR(40)    NOT NULL,
    name         VARCHAR(100)   NOT NULL,
    description  VARCHAR(500),
    price        NUMERIC(12, 2) NOT NULL DEFAULT 0,
    currency     VARCHAR(3)     NOT NULL DEFAULT 'PEN',
    status       VARCHAR(20)    NOT NULL DEFAULT 'DRAFT',
    created_at   TIMESTAMPTZ    NOT NULL,
    created_by   UUID,
    updated_at   TIMESTAMPTZ,
    updated_by   UUID,
    version      BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT ck_plan_code     CHECK (code ~ '^[A-Z0-9_]{3,40}$'),
    CONSTRAINT ck_plan_status   CHECK (status IN ('DRAFT', 'PUBLISHED', 'RETIRED')),
    CONSTRAINT ck_plan_price    CHECK (price >= 0),
    CONSTRAINT ck_plan_currency CHECK (currency ~ '^[A-Z]{3}$')
);
CREATE UNIQUE INDEX ux_plan_code ON plan (code);

CREATE TABLE plan_module (
    plan_id      UUID        NOT NULL REFERENCES plan (id),
    module_code  VARCHAR(40) NOT NULL REFERENCES module (code),
    PRIMARY KEY (plan_id, module_code)
);

-- ---------------------------------------------------------------- contrato
ALTER TABLE contract
    ADD COLUMN plan_id               UUID REFERENCES plan (id),
    ADD COLUMN plan_name             VARCHAR(100),
    ADD COLUMN price                 NUMERIC(12, 2) NOT NULL DEFAULT 0,
    ADD COLUMN currency              VARCHAR(3)     NOT NULL DEFAULT 'PEN',
    ADD COLUMN max_branches          INT,
    ADD COLUMN renewal_type          VARCHAR(20)    NOT NULL DEFAULT 'NEW',
    ADD COLUMN previous_contract_id  UUID REFERENCES contract (id),
    ADD COLUMN status_reason         VARCHAR(255),
    ADD COLUMN activated_at          TIMESTAMPTZ,
    ADD COLUMN suspended_at          TIMESTAMPTZ,
    ADD COLUMN cancelled_at          TIMESTAMPTZ,
    ADD COLUMN replaced_at           TIMESTAMPTZ,
    ADD COLUMN expired_at            TIMESTAMPTZ;

ALTER TABLE contract ADD CONSTRAINT ck_contract_price    CHECK (price >= 0);
ALTER TABLE contract ADD CONSTRAINT ck_contract_currency CHECK (currency ~ '^[A-Z]{3}$');
ALTER TABLE contract ADD CONSTRAINT ck_contract_maxbr    CHECK (max_branches IS NULL OR max_branches >= 1);
ALTER TABLE contract ADD CONSTRAINT ck_contract_renewal  CHECK (renewal_type IN ('NEW', 'RENEWAL', 'UPGRADE', 'DOWNGRADE'));
-- [V7] un contrato por sede lleva sede; uno de organización no.
ALTER TABLE contract ADD CONSTRAINT ck_contract_branch   CHECK ((scope = 'BRANCH' AND branch_id IS NOT NULL) OR (scope = 'ORGANIZATION' AND branch_id IS NULL));

CREATE INDEX ix_contract_previous ON contract (previous_contract_id);
CREATE INDEX ix_contract_end      ON contract (status, end_date);

-- Contratos creados antes de M03 (datos de desarrollo): ya estaban vigentes.
UPDATE contract SET activated_at = created_at WHERE status = 'ACTIVE' AND activated_at IS NULL;

-- Avisos de vencimiento: una fila por (contrato, umbral en días) garantiza "una vez por umbral".
CREATE TABLE contract_expiry_notice (
    contract_id     UUID        NOT NULL REFERENCES contract (id),
    threshold_days  INT         NOT NULL,
    sent_at         TIMESTAMPTZ NOT NULL,
    recipients      INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (contract_id, threshold_days),
    CONSTRAINT ck_notice_threshold CHECK (threshold_days IN (30, 15, 7))
);

-- ---------------------------------------------------------------- catálogo
-- MODULE_CATALOG, PLAN y CONTRACT: gestión de N1. ORG_CONTRACT_VIEW: "Contrato" de solo lectura para ORG_ADMIN (N2).
INSERT INTO module (code, name_es, name_en, levels, kind, actions, delegable, route, icon, sort_order) VALUES
 ('CONTRACT',          'Contratos',            'Contracts',         ARRAY['N1'], 'BASE', ARRAY['V','C','E','S'], FALSE, 'contracts',     'file-protect', 120),
 ('PLAN',              'Planes',               'Plans',             ARRAY['N1'], 'BASE', ARRAY['V','C','E','S'], FALSE, 'plans',         'profile',      130),
 ('MODULE_CATALOG',    'Catálogo de módulos',  'Module catalog',    ARRAY['N1'], 'BASE', ARRAY['V','C','E','S'], FALSE, 'modules',       'appstore',     140),
 ('ORG_CONTRACT_VIEW', 'Contrato',             'Contract',          ARRAY['N2'], 'BASE', ARRAY['V'],             FALSE, 'contract',      'file-protect', 110);
