-- V28 · M17 RRHH y planilla: tres módulos contratables — HR_STAFF (ficha del personal), HR_LEAVE (vacaciones y permisos, con
-- aprobación reusando ApprovalEngine tipo LEAVE_REQUEST) y HR_PAYROLL (conceptos + corridas mensuales con boleta y egreso hacia
-- M15). Reusa `organization.working_days` y `org_holiday` (ambas de M23, sembradas por adelantado "para M15, M17 y M20") para
-- los días hábiles de las ausencias [V10/M17-T11].
--
-- [D1] "Por defecto NO delegado" del spec N3 (línea 21: "ORG_BRANCH_ADMIN/ORG_USER V C E A ... si N2 delega") no se puede
-- implementar como una delegación real para ORG_BRANCH_ADMIN: `AuthorizationService.effectiveActions()` resuelve ese rol por
-- `branchAdminActions()` (un mapa de tope fijo en código, `BRANCH_ADMIN_CAPS`), no por `user_access_permission` — ese camino de
-- delegación explícita solo existe para ORG_USER. Extender el modelo de autorización para que un `ORG_BRANCH_ADMIN` reciba
-- también delegaciones puntuales tocaría código central ya validado por 16 módulos previos, así que se implementa el efecto
-- equivalente sin tocarlo: los tres módulos entran en `BRANCH_ADMIN_CAPS` con conjunto vacío (ningún administrador de sede
-- recibe nada por su rol) y, como los tres módulos se registran `delegable=true`, la organización delega puntualmente a
-- personas concretas (incluida la aprobación de permisos de su equipo) por la vía ya existente de ORG_USER + permisos
-- delegados — que es exactamente lo que el spec describe como "si N2 delega".
--
-- [D2] El certificado/CertificateService de M08 exige `rite_id` y no generaliza a boletas de pago (mismo motivo que llevó a
-- M13 a construir su propio numerador en vez de forzarlo); la boleta usa su propio contador correlativo
-- (`payroll_record_counter`, mismo patrón UPSERT que `fin_receipt_counter`/`certificate_counter`/`training_certificate_counter`)
-- y por ahora es una vista imprimible, no un PDF con plantilla real — eso llega con M18 (el spec ya declara esa dependencia).
--
-- [D3] "NumberingService"/"ExportService" del núcleo 01 nunca se construyeron como clases compartidas (ver 02_PROGRESO:
-- "Pendiente del núcleo 01 §2"); como en todos los módulos anteriores, se usa un contador local (D2) y `XlsxWriter` directo.

INSERT INTO module (code, name_es, name_en, levels, kind, actions, delegable, route, icon, sort_order) VALUES
 ('HR_STAFF',   'Personal',            'Staff',           ARRAY['N2','N3'], 'CONTRACTABLE', ARRAY['V','C','E','D','S','X','H'],         TRUE, 'hr/staff',   'idcard',    330),
 ('HR_LEAVE',   'Vacaciones y permisos','Leave & absences', ARRAY['N2','N3'], 'CONTRACTABLE', ARRAY['V','C','E','S','A','X'],             TRUE, 'hr/leave',   'calendar',  331),
 ('HR_PAYROLL', 'Planilla',            'Payroll',         ARRAY['N2','N3'], 'CONTRACTABLE', ARRAY['V','C','E','A','P','K','Y','X','H'], TRUE, 'hr/payroll', 'wallet',    332);

-- ---------------------------------------------------------------- personal
CREATE TABLE staff_member (
    id                 UUID PRIMARY KEY,
    organization_id    UUID         NOT NULL REFERENCES organization (id),
    branch_id          UUID         NOT NULL REFERENCES branch (id),
    person_id          UUID         NOT NULL REFERENCES person (id),
    position           VARCHAR(120) NOT NULL,
    ministry_id        UUID         REFERENCES ministry (id),
    contract_type      VARCHAR(24)  NOT NULL,
    hire_date          DATE         NOT NULL,
    contract_end       DATE,
    termination_date   DATE,
    termination_reason VARCHAR(300),
    base_salary        NUMERIC(14, 2) NOT NULL,
    currency           VARCHAR(3)   NOT NULL DEFAULT 'PEN',
    pay_frequency      VARCHAR(10)  NOT NULL DEFAULT 'MONTHLY',
    bank_encrypted     TEXT,                                                                                     -- SecretCipher (mismo patrón que person.private_notes)
    status             VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    created_at         TIMESTAMPTZ  NOT NULL,
    created_by         UUID,
    updated_at         TIMESTAMPTZ,
    updated_by         UUID,
    version            BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_staff_contract_type CHECK (contract_type IN ('PLANILLA_INDEFINIDO', 'PLANILLA_PLAZO_FIJO', 'RECIBO_HONORARIOS', 'PRACTICAS', 'OTRO')),
    CONSTRAINT ck_staff_pay_frequency CHECK (pay_frequency IN ('MONTHLY', 'BIWEEKLY', 'WEEKLY')),
    CONSTRAINT ck_staff_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'TERMINATED')),
    CONSTRAINT ck_staff_salary CHECK (base_salary >= 0),                                                         -- [V3]
    CONSTRAINT ck_staff_termination CHECK (termination_date IS NULL OR termination_date >= hire_date),           -- [V2]
    CONSTRAINT ck_staff_contract_end CHECK (contract_type NOT IN ('PLANILLA_PLAZO_FIJO', 'PRACTICAS') OR contract_end IS NOT NULL) -- [V2]
);
-- [V1] sin otra ficha activa: bloquea mientras exista una ACTIVE o SUSPENDED del mismo person_id (una TERMINATED no bloquea, permite recontratar)
CREATE UNIQUE INDEX ux_staff_active_person ON staff_member (person_id) WHERE status <> 'TERMINATED';
CREATE INDEX ix_staff_org_branch ON staff_member (organization_id, branch_id, status);

CREATE TABLE salary_history (
    id         UUID PRIMARY KEY,
    staff_id   UUID         NOT NULL REFERENCES staff_member (id),
    amount     NUMERIC(14, 2) NOT NULL,
    from_date  DATE         NOT NULL,
    reason     VARCHAR(300),
    created_at TIMESTAMPTZ  NOT NULL,
    created_by UUID,
    CONSTRAINT ck_salary_amount CHECK (amount >= 0)
);
CREATE INDEX ix_salary_history_staff ON salary_history (staff_id, from_date DESC);

-- ---------------------------------------------------------------- vacaciones y permisos
CREATE TABLE leave_request (
    id                  UUID PRIMARY KEY,
    organization_id     UUID         NOT NULL REFERENCES organization (id),
    staff_id            UUID         NOT NULL REFERENCES staff_member (id),
    type                VARCHAR(24)  NOT NULL,
    start_date          DATE         NOT NULL,
    end_date            DATE         NOT NULL,
    days                NUMERIC(5, 1) NOT NULL,
    reason              VARCHAR(500),
    attachment_key      VARCHAR(300),
    status              VARCHAR(10)  NOT NULL DEFAULT 'PENDING',
    decided_by          UUID,
    decision_reason     VARCHAR(500),
    approval_request_id UUID,
    created_at          TIMESTAMPTZ  NOT NULL,
    created_by          UUID,
    updated_at          TIMESTAMPTZ,
    updated_by          UUID,
    version             BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_leave_type CHECK (type IN ('VACATION', 'PERSONAL_PERMIT', 'SICK_LEAVE', 'MATERNITY_PATERNITY', 'UNPAID_LEAVE', 'OTHER')),
    CONSTRAINT ck_leave_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT ck_leave_range CHECK (end_date >= start_date),                                                    -- [V10]
    CONSTRAINT ck_leave_days CHECK (days > 0)
);
CREATE INDEX ix_leave_staff_status ON leave_request (staff_id, status, start_date);
CREATE INDEX ix_leave_org_status ON leave_request (organization_id, status);

CREATE TABLE leave_balance (
    id        UUID PRIMARY KEY,
    staff_id  UUID          NOT NULL REFERENCES staff_member (id),
    year      INT           NOT NULL,
    entitled  NUMERIC(5, 1) NOT NULL,
    taken     NUMERIC(5, 1) NOT NULL DEFAULT 0,
    pending   NUMERIC(5, 1) NOT NULL DEFAULT 0,
    CONSTRAINT ux_leave_balance UNIQUE (staff_id, year)
);

-- ---------------------------------------------------------------- planilla
CREATE TABLE payroll_concept (
    id               UUID         PRIMARY KEY,
    organization_id  UUID         NOT NULL REFERENCES organization (id),
    code             VARCHAR(30)  NOT NULL,
    name_es          VARCHAR(120) NOT NULL,
    name_en          VARCHAR(120) NOT NULL,
    kind             VARCHAR(24)  NOT NULL,
    calc             VARCHAR(20)  NOT NULL,
    value            NUMERIC(10, 4),                                                                             -- % (0-100) o monto fijo, según calc; NULL si MANUAL
    mandatory        BOOLEAN      NOT NULL DEFAULT FALSE,
    active            BOOLEAN     NOT NULL DEFAULT TRUE,
    sort_order       INT          NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL,
    created_by       UUID,
    updated_at       TIMESTAMPTZ,
    updated_by       UUID,
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ux_concept_code UNIQUE (organization_id, code),
    CONSTRAINT ck_concept_kind CHECK (kind IN ('EARNING', 'DEDUCTION', 'EMPLOYER_CONTRIBUTION')),
    CONSTRAINT ck_concept_calc CHECK (calc IN ('FIXED', 'PERCENT_OF_BASE', 'MANUAL')),
    CONSTRAINT ck_concept_value CHECK (calc = 'MANUAL' OR value IS NOT NULL),                                     -- [V4]
    CONSTRAINT ck_concept_percent CHECK (calc <> 'PERCENT_OF_BASE' OR (value >= 0 AND value <= 100))              -- [V4]
);

CREATE TABLE payroll_run (
    id                   UUID        PRIMARY KEY,
    organization_id      UUID        NOT NULL REFERENCES organization (id),
    branch_id            UUID        REFERENCES branch (id),                                                     -- NULL = corrida de toda la organización
    period               VARCHAR(7)  NOT NULL,                                                                    -- 'YYYY-MM'
    status               VARCHAR(12) NOT NULL DEFAULT 'DRAFT',
    calculated_at        TIMESTAMPTZ,
    approved_at          TIMESTAMPTZ,
    approved_by          UUID,
    paid_at              TIMESTAMPTZ,
    paid_by              UUID,
    closed_at            TIMESTAMPTZ,
    closed_by            UUID,
    financial_movement_id UUID,                                                                                   -- [D2 de M16, reusado] enlace idempotente al egreso PAYROLL en fin_movement
    created_at           TIMESTAMPTZ NOT NULL,
    created_by           UUID,
    updated_at           TIMESTAMPTZ,
    updated_by           UUID,
    version              BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_run_status CHECK (status IN ('DRAFT', 'CALCULATED', 'APPROVED', 'PAID', 'CLOSED')),
    CONSTRAINT ck_run_period CHECK (period ~ '^\d{4}-(0[1-9]|1[0-2])$')
);
-- [V5] única (org, sede, periodo) mientras no esté CLOSED
CREATE UNIQUE INDEX ux_run_period ON payroll_run (organization_id, COALESCE(branch_id, '00000000-0000-0000-0000-000000000000'::uuid), period) WHERE status <> 'CLOSED';

CREATE TABLE payroll_record (
    id             UUID PRIMARY KEY,
    run_id         UUID           NOT NULL REFERENCES payroll_run (id),
    staff_id       UUID           NOT NULL REFERENCES staff_member (id),
    gross          NUMERIC(14, 2) NOT NULL DEFAULT 0,
    deductions     NUMERIC(14, 2) NOT NULL DEFAULT 0,
    net            NUMERIC(14, 2) NOT NULL DEFAULT 0,
    employer_cost  NUMERIC(14, 2) NOT NULL DEFAULT 0,
    worked_days    NUMERIC(5, 1)  NOT NULL DEFAULT 0,
    unpaid_days    NUMERIC(5, 1)  NOT NULL DEFAULT 0,
    payslip_no     VARCHAR(20),
    created_at     TIMESTAMPTZ    NOT NULL,
    updated_at     TIMESTAMPTZ,
    CONSTRAINT ux_record_run_staff UNIQUE (run_id, staff_id)
);
CREATE UNIQUE INDEX ux_payslip_no ON payroll_record (payslip_no) WHERE payslip_no IS NOT NULL;                   -- [V8] correlativo sin huecos

CREATE TABLE payroll_record_line (
    id            UUID PRIMARY KEY,
    record_id     UUID           NOT NULL REFERENCES payroll_record (id),
    concept_id    UUID           REFERENCES payroll_concept (id),
    concept_code  VARCHAR(30)    NOT NULL,                                                                        -- snapshot: una boleta APROBADA no cambia si el concepto se edita después
    concept_name  VARCHAR(120)   NOT NULL,
    kind          VARCHAR(24)    NOT NULL,
    amount        NUMERIC(14, 2) NOT NULL
);
CREATE INDEX ix_record_line_record ON payroll_record_line (record_id);

CREATE TABLE payroll_record_counter (
    organization_id UUID NOT NULL,
    year            INT  NOT NULL,
    last_number     INT  NOT NULL DEFAULT 0,
    PRIMARY KEY (organization_id, year)
);
