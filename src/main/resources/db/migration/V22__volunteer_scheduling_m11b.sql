-- V22 · M11 (parte 2) Turnos y programación de voluntarios (VOLUNTEER_SCHEDULING): planes de servicio, turnos por ministerio/cargo, asignaciones con confirmación,
-- disponibilidad y reglas por organización. Reusa AttendanceService (núcleo M09, contexto SHIFT, un session por turno) y MinistrySupport (elegibilidad y verificación [V6]).
-- Portal (N4: mis turnos, confirmar/rechazar, disponibilidad, unirme) queda para M24, igual que en M09/M10/M11a.

INSERT INTO module (code, name_es, name_en, levels, kind, actions, delegable, route, icon, sort_order) VALUES
 ('VOLUNTEER_SCHEDULING', 'Turnos de voluntarios', 'Volunteer scheduling', ARRAY['N2','N3'], 'CONTRACTABLE', ARRAY['V','C','E','D','S','P','X'], TRUE, 'volunteer-scheduling', 'schedule', 265);

-- ---------------------------------------------------------------- plan de servicio (culto o evento, en una fecha)
CREATE TABLE service_plan (
    id               UUID PRIMARY KEY,
    organization_id  UUID         NOT NULL REFERENCES organization (id),
    branch_id        UUID         NOT NULL REFERENCES branch (id),
    plan_date        DATE         NOT NULL,
    context_type     VARCHAR(8)   NOT NULL,                     -- SERVICE (church_service) | EVENT (M14, aún no existe: se guarda sin FK)
    context_id       UUID,                                      -- church_service.id cuando context_type = SERVICE
    title            VARCHAR(120) NOT NULL,
    status           VARCHAR(10)  NOT NULL DEFAULT 'DRAFT',
    published_at     TIMESTAMPTZ,
    closed_at        TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL,
    created_by       UUID,
    updated_at       TIMESTAMPTZ,
    updated_by       UUID,
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_splan_context CHECK (context_type IN ('SERVICE', 'EVENT')),
    CONSTRAINT ck_splan_status  CHECK (status IN ('DRAFT', 'PUBLISHED', 'CLOSED'))
);
CREATE UNIQUE INDEX ux_splan_occurrence ON service_plan (branch_id, context_type, context_id, plan_date) WHERE context_id IS NOT NULL;
CREATE INDEX ix_splan_branch ON service_plan (branch_id, plan_date);

-- ---------------------------------------------------------------- turno (ministerio, cargo, cantidad y horario dentro del plan)
CREATE TABLE shift_slot (
    id                  UUID PRIMARY KEY,
    plan_id             UUID         NOT NULL REFERENCES service_plan (id),
    branch_ministry_id  UUID         NOT NULL REFERENCES branch_ministry (id),
    position_id         UUID         REFERENCES ministry_position (id),      -- null = cualquier cargo del ministerio
    needed              INT          NOT NULL DEFAULT 1,
    start_time          TIME         NOT NULL,
    end_time            TIME         NOT NULL,
    notes               VARCHAR(300),
    created_at          TIMESTAMPTZ  NOT NULL,
    created_by          UUID,
    version             BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_slot_needed CHECK (needed >= 1),
    CONSTRAINT ck_slot_time   CHECK (end_time > start_time)
);
CREATE INDEX ix_slot_plan ON shift_slot (plan_id);
CREATE INDEX ix_slot_bmin ON shift_slot (branch_ministry_id);

-- ---------------------------------------------------------------- asignación de una persona a un turno
CREATE TABLE shift_assignment (
    id               UUID PRIMARY KEY,
    slot_id          UUID         NOT NULL REFERENCES shift_slot (id),
    person_id        UUID         NOT NULL REFERENCES person (id),
    status           VARCHAR(10)  NOT NULL DEFAULT 'PROPOSED',
    decline_reason   VARCHAR(300),
    responded_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL,
    created_by       UUID,
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_sassign_status CHECK (status IN ('PROPOSED', 'CONFIRMED', 'DECLINED', 'NO_SHOW', 'SERVED')),
    CONSTRAINT ux_sassign UNIQUE (slot_id, person_id)
);
CREATE INDEX ix_sassign_person ON shift_assignment (person_id, status);

-- ---------------------------------------------------------------- no disponibilidad de una persona (bloquea la asignación salvo override con motivo [V10])
CREATE TABLE availability (
    id               UUID PRIMARY KEY,
    organization_id  UUID         NOT NULL REFERENCES organization (id),
    person_id        UUID         NOT NULL REFERENCES person (id),
    from_date        DATE         NOT NULL,
    to_date          DATE,                                      -- null con recurrence WEEKLY = indefinido
    recurrence       VARCHAR(6)   NOT NULL DEFAULT 'ONCE',
    day_of_week      SMALLINT,                                  -- 1 = lunes … 7 = domingo (ISO); requerido si recurrence = WEEKLY
    reason           VARCHAR(200),
    created_at       TIMESTAMPTZ  NOT NULL,
    created_by       UUID,
    CONSTRAINT ck_avail_recurrence CHECK (recurrence IN ('ONCE', 'WEEKLY')),
    CONSTRAINT ck_avail_dates      CHECK (to_date IS NULL OR to_date >= from_date),
    CONSTRAINT ck_avail_dow        CHECK (recurrence = 'ONCE' OR (day_of_week BETWEEN 1 AND 7))
);
CREATE INDEX ix_avail_person ON availability (person_id, from_date);

-- ---------------------------------------------------------------- reglas de voluntariado por organización
CREATE TABLE volunteer_rules (
    organization_id      UUID PRIMARY KEY REFERENCES organization (id),
    max_shifts_per_month INT          NOT NULL DEFAULT 4,
    reminder_hours_1     INT          NOT NULL DEFAULT 48,       -- primer recordatorio (horas antes del turno); null = desactivado
    reminder_hours_2     INT          NOT NULL DEFAULT 2,        -- segundo recordatorio; null = desactivado
    decline_lock_hours   INT          NOT NULL DEFAULT 24,       -- rechazar dentro de esta ventana exige motivo [V12]
    updated_at           TIMESTAMPTZ,
    updated_by           UUID,
    version              BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_vrules_max     CHECK (max_shifts_per_month >= 1),
    CONSTRAINT ck_vrules_lock    CHECK (decline_lock_hours >= 0)
);
