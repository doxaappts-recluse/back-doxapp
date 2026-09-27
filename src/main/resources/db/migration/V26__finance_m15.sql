-- =====================================================================================================
-- M15 · Finanzas y donaciones
-- Módulos CONTRACTABLE: FIN_MOVEMENTS (núcleo: movimientos, cuentas, caja, conteo), FIN_FUNDS (fondos),
-- FIN_BUDGETS (presupuestos), FIN_DONORS (donantes y promesas). N2/N3, delegables.
-- ONLINE_GIVING (F9, cobro en línea con webhook/tokenización) queda FUERA de esta versión: es opcional
-- (F9) y depende de un proveedor de pagos real; cuando exista, sus movimientos entrarán por el mismo
-- FinancialMovement con method=CARD y sourceRef=ONLINE_GIVING, sin tabla nueva.
--
-- Desviaciones de diseño (documentadas, mismo criterio que M13/M14):
-- [D1] FIN_MOVEMENT NO usa el ApprovalEngine genérico (approval_request). El estado (PENDING/APPROVED/
--      REJECTED/VOIDED) vive directo en financial_movement porque: (a) la regla "quien registra no
--      aprueba" es CONFIGURABLE por organización (finance_rules.self_approval, default false), mientras
--      que la V1 del motor genérico es fija y no configurable; (b) hay una regla de UMBRAL POR MONTO
--      (approvalThresholdBranch) que enruta a la administración de la organización cuando el monto la
--      supera, y el motor genérico no modela umbrales; (c) el propio spec del módulo pone el estado como
--      campo de FinancialMovement, no como una solicitud aparte. Se implementa a mano en
--      FinancialMovementService con las mismas garantías (no se puede decidir lo propio salvo bandera,
--      alcance de sede, motivo obligatorio al rechazar/anular).
-- [D2] Constancia anual de donación (DONATION_CERTIFICATE) es exclusiva del portal N4 (Mis donaciones),
--      que como todo N4 queda diferido a M24 igual que en los módulos anteriores. Por eso NO se crea
--      ningún CertificateService para M15 en esta entrega: el recibo (receiptNo) alcanza para N2/N3.
-- [D3] Numeración de recibo: un contador por organización y año (finance_receipt_counter), con UPSERT
--      atómico (mismo patrón que certificate_counter de M08 y training_certificate_counter de M13),
--      que Postgres serializa por fila sin huecos ni duplicados bajo concurrencia [V19/M15-T11].
-- [D4] Categorías de movimiento: lista fija por CHECK (no CatalogTypeRegistry) porque la coherencia
--      categoría↔tipo [V7] es una regla de negocio dura que el framework de catálogos no valida; las
--      categorías de egreso no las enumera el spec así que se define un conjunto razonable y documentado
--      (EXPENSE genérico + PAYROLL/MAINTENANCE/UTILITIES/SUPPLIES/OTHER_EXPENSE) ampliable en una futura
--      migración si se requiere.
-- [D5] Eventos (M14) y ritos (M08) generan FinancialMovement por API desde este módulo cuando ambos
--      estén contratados (sourceRef=EVENT/RITE, eventId), pero esta entrega NO migra el pago provisional
--      de M14 (event_registration.payment_*) a FinancialMovement todavía: es un cambio que toca M14 y se
--      hará en una entrega de integración aparte para no romper lo ya validado; queda anotado como
--      pendiente en el "como_ejecutar".
-- [D6] Adjuntos de comprobante: tabla propia fin_movement_attachment (varios por movimiento) sobre
--      FileStorageService, mismo patrón que los adjuntos de Soporte (M22).
-- =====================================================================================================

-- Nuevas acciones del núcleo (Action): Y anular, K cerrar caja/periodo, Z reabrir periodo (agregadas al enum Java, sin CHECK en BD).

insert into module (code, name_es, name_en, levels, kind, actions, delegable, route, icon, sort_order) values
('FIN_MOVEMENTS', 'Finanzas: movimientos y caja', 'Finance: movements and cash', array['N2','N3'], 'CONTRACTABLE',
    array['V','C','E','S','A','Y','K','Z','X'], true, 'finance/movements', 'wallet', 300),
('FIN_FUNDS', 'Finanzas: fondos', 'Finance: funds', array['N2'], 'CONTRACTABLE',
    array['V','C','E','D','X'], false, 'finance/funds', 'fund', 301),
('FIN_BUDGETS', 'Finanzas: presupuestos', 'Finance: budgets', array['N2','N3'], 'CONTRACTABLE',
    array['V','C','E','A','X'], true, 'finance/budgets', 'budget', 302),
('FIN_DONORS', 'Finanzas: donantes y promesas', 'Finance: donors and pledges', array['N2','N3'], 'CONTRACTABLE',
    array['V','C','E','D','X'], true, 'finance/donors', 'donor', 303);

-- ---------------------------------------------------------------- fondos
create table fin_fund (
    id                uuid primary key,
    organization_id   uuid         not null references organization (id),
    code              varchar(20)  not null,
    name              varchar(120) not null,
    type              varchar(12)  not null,
    allowed_categories text[],                                                                                 -- null = todas
    currency          varchar(3)   not null default 'PEN',
    status            varchar(10)  not null default 'ACTIVE',
    created_at        timestamptz  not null,
    created_by        uuid,
    updated_at        timestamptz,
    updated_by        uuid,
    version           bigint       not null default 0,
    constraint uq_fund_code unique (organization_id, code),
    constraint ck_fund_type check (type in ('GENERAL','RESTRICTED','BUILDING','MISSIONS','OTHER')),
    constraint ck_fund_status check (status in ('ACTIVE','INACTIVE'))
);

-- ---------------------------------------------------------------- cuentas
create table fin_account (
    id               uuid primary key,
    organization_id  uuid         not null references organization (id),
    branch_id        uuid references branch (id),                                                             -- null = de toda la organización
    name             varchar(120) not null,
    type             varchar(10)  not null,
    currency         varchar(3)   not null default 'PEN',
    opening_balance  numeric(14,2) not null default 0,
    status           varchar(10)  not null default 'ACTIVE',
    created_at       timestamptz  not null,
    created_by       uuid,
    updated_at       timestamptz,
    updated_by       uuid,
    version          bigint       not null default 0,
    constraint ck_account_type check (type in ('CASH','BANK','WALLET')),
    constraint ck_account_status check (status in ('ACTIVE','INACTIVE')),
    constraint ck_account_opening check (opening_balance >= 0)
);

-- ---------------------------------------------------------------- donantes
create table fin_donor (
    id               uuid primary key,
    organization_id  uuid        not null references organization (id),
    person_id        uuid references person (id),
    external_name    varchar(150),
    external_doc_id  varchar(20),
    email            varchar(150),
    created_at       timestamptz not null,
    created_by       uuid,
    version          bigint      not null default 0,
    constraint ck_donor_identity check (person_id is not null or external_name is not null),
    constraint uq_donor_person unique (organization_id, person_id)
);

-- ---------------------------------------------------------------- periodos fiscales
create table fin_fiscal_period (
    id               uuid primary key,
    organization_id  uuid        not null references organization (id),
    year             int         not null,
    month            int         not null,
    status           varchar(10) not null default 'OPEN',
    closed_at        timestamptz,
    closed_by        uuid,
    reopened_at      timestamptz,
    reopened_by      uuid,
    reopen_reason    varchar(500),
    constraint uq_period unique (organization_id, year, month),
    constraint ck_period_month check (month between 1 and 12),
    constraint ck_period_status check (status in ('OPEN','CLOSED'))
);

-- ---------------------------------------------------------------- reglas (singleton por organización)
create table fin_rules (
    organization_id           uuid primary key references organization (id),
    approval_threshold_branch numeric(14,2) not null default 500,
    attachment_threshold      numeric(14,2) not null default 200,
    overspend_block           boolean       not null default false,
    self_approval             boolean       not null default false,
    updated_at                timestamptz,
    updated_by                uuid,
    constraint ck_rules_thresholds check (approval_threshold_branch >= 0 and attachment_threshold >= 0)
);

-- ---------------------------------------------------------------- cajas
create table fin_cash_register (
    id               uuid primary key,
    organization_id  uuid        not null references organization (id),
    branch_id        uuid        not null references branch (id),
    register_date    date        not null,
    opened_by        uuid        not null,
    opening_amount   numeric(14,2) not null default 0,
    expected_amount  numeric(14,2),
    counted_amount   numeric(14,2),
    difference       numeric(14,2),
    notes            varchar(500),
    status           varchar(10) not null default 'OPEN',
    closed_by        uuid,
    closed_at        timestamptz,
    created_at       timestamptz not null,
    version          bigint      not null default 0,
    constraint ck_register_status check (status in ('OPEN','CLOSED')),
    constraint ck_register_opening check (opening_amount >= 0)
);
create unique index ux_register_open on fin_cash_register (branch_id, register_date, opened_by) where status = 'OPEN';    -- [12]

-- ---------------------------------------------------------------- movimientos
create table fin_movement (
    id               uuid primary key,
    organization_id  uuid        not null references organization (id),
    branch_id        uuid        not null references branch (id),
    movement_date    date        not null,
    type             varchar(7)  not null,
    category         varchar(20) not null,
    fund_id          uuid        not null references fin_fund (id),
    account_id       uuid        references fin_account (id),
    amount           numeric(14,2) not null,
    currency         varchar(3)  not null default 'PEN',
    method           varchar(10) not null,
    donor_id         uuid        references fin_donor (id),
    anonymous        boolean     not null default false,
    description      varchar(500),
    receipt_no       varchar(30),
    event_id         uuid,                                                                                     -- org_event (M14), sin FK dura: EVENTS puede no estar contratado
    source_ref       varchar(20),                                                                              -- RITE|PAYROLL|INVENTORY|EVENT, null = manual
    cash_register_id uuid        references fin_cash_register (id),
    offering_count_id uuid,                                                                                    -- fin_offering_count, ver abajo (FK agregada después de crear esa tabla)
    status           varchar(10) not null default 'PENDING',
    submitted_by     uuid        not null,
    decided_by       uuid,
    decided_at       timestamptz,
    void_reason      varchar(500),
    voided_by        uuid,
    voided_at        timestamptz,
    reversal_of      uuid references fin_movement (id),
    created_at       timestamptz not null,
    created_by       uuid,
    updated_at       timestamptz,
    updated_by       uuid,
    version          bigint      not null default 0,
    constraint ck_movement_type check (type in ('INCOME','EXPENSE')),
    constraint ck_movement_category check (category in
        ('TITHE','OFFERING','DONATION','OTHER_INCOME','SERVICE_FEE',
         'EXPENSE','PAYROLL','MAINTENANCE','UTILITIES','SUPPLIES','OTHER_EXPENSE')),                            -- [D4]
    constraint ck_movement_method check (method in ('CASH','TRANSFER','CARD','OTHER')),
    constraint ck_movement_status check (status in ('PENDING','APPROVED','REJECTED','VOIDED')),
    constraint ck_movement_amount check (amount > 0 and amount <= 9999999.99)                                   -- [V4]
);
create index ix_movement_org_branch_date on fin_movement (organization_id, branch_id, movement_date desc);
create index ix_movement_status on fin_movement (organization_id, status);
create index ix_movement_fund on fin_movement (fund_id);
create index ix_movement_donor on fin_movement (donor_id) where donor_id is not null;
create index ix_movement_register on fin_movement (cash_register_id) where cash_register_id is not null;

create table fin_movement_attachment (
    id            uuid primary key,
    movement_id   uuid        not null references fin_movement (id),
    storage_key   varchar(300) not null,
    filename      varchar(200) not null,
    uploaded_at   timestamptz not null,
    uploaded_by   uuid
);
create index ix_movement_attachment on fin_movement_attachment (movement_id);

-- ---------------------------------------------------------------- conteo de ofrenda
create table fin_offering_count (
    id                uuid primary key,
    organization_id   uuid        not null references organization (id),
    branch_id         uuid        not null references branch (id),
    session_id        uuid        references attendance_session (id),                                          -- culto (M09), opcional
    counted_by_1      uuid        not null,
    counted_by_2      uuid        not null,
    cash_breakdown    jsonb,                                                                                    -- denominaciones {"100":2,"50":3,...}
    checks_amount     numeric(14,2) not null default 0,
    total             numeric(14,2) not null default 0,
    status            varchar(10) not null default 'DRAFT',
    confirmed_1_at    timestamptz,
    confirmed_2_at    timestamptz,
    created_at        timestamptz not null,
    created_by        uuid,
    version           bigint      not null default 0,
    constraint ck_count_status check (status in ('DRAFT','CONFIRMED')),
    constraint ck_count_distinct check (counted_by_1 <> counted_by_2)                                           -- [V13]
);
create table fin_offering_count_fund (
    count_id  uuid not null references fin_offering_count (id),
    fund_id   uuid not null references fin_fund (id),
    amount    numeric(14,2) not null check (amount > 0),
    primary key (count_id, fund_id)
);
alter table fin_movement add constraint fk_movement_offering_count foreign key (offering_count_id) references fin_offering_count (id);

-- ---------------------------------------------------------------- presupuestos
create table fin_budget (
    id               uuid primary key,
    organization_id  uuid        not null references organization (id),
    scope            varchar(6)  not null,
    branch_id        uuid        references branch (id),
    fund_id          uuid        not null references fin_fund (id),
    category         varchar(20) not null,
    period           date        not null,                                                                     -- primer día del mes del periodo
    amount           numeric(14,2) not null,
    status           varchar(10) not null default 'DRAFT',
    created_at       timestamptz not null,
    created_by       uuid,
    updated_at       timestamptz,
    updated_by       uuid,
    version          bigint      not null default 0,
    constraint ck_budget_scope check (scope in ('ORG','BRANCH')),
    constraint ck_budget_branch check ((scope = 'BRANCH') = (branch_id is not null)),
    constraint ck_budget_status check (status in ('DRAFT','APPROVED','CLOSED')),
    constraint ck_budget_amount check (amount > 0),
    constraint uq_budget unique (organization_id, scope, branch_id, fund_id, category, period)                  -- [V2]
);

-- ---------------------------------------------------------------- promesas
create table fin_pledge (
    id               uuid primary key,
    organization_id  uuid        not null references organization (id),
    donor_id         uuid        not null references fin_donor (id),
    fund_id          uuid        not null references fin_fund (id),
    amount           numeric(14,2) not null,
    frequency        varchar(10) not null,
    start_date       date        not null,
    end_date         date,
    status           varchar(10) not null default 'ACTIVE',
    created_at       timestamptz not null,
    created_by       uuid,
    version          bigint      not null default 0,
    constraint ck_pledge_freq check (frequency in ('ONE_TIME','WEEKLY','MONTHLY','YEARLY')),
    constraint ck_pledge_status check (status in ('ACTIVE','FULFILLED','CANCELLED')),
    constraint ck_pledge_amount check (amount > 0),
    constraint ck_pledge_dates check (end_date is null or end_date >= start_date)                               -- [V18]
);

-- ---------------------------------------------------------------- numeración de recibo [D3]
create table fin_receipt_counter (
    organization_id  uuid    not null references organization (id),
    year             int     not null,
    last_number      int     not null default 0,
    primary key (organization_id, year)
);

-- ---------------------------------------------------------------- catálogo de módulo en BRANCH_ADMIN_CAPS (ver AuthorizationService.java)
