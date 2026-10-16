-- V25 · M14 Eventos e inscripciones: eventos de organización o de sede, tarifas por categoría, preguntas del formulario, inscripciones con lista de
-- espera y ticket QR. El dinero es de M15 (no existe aún): el pago queda en una tabla propia y provisional dentro de event_registration (comprobante de
-- texto, confirmación por otra persona) hasta que M15 defina FinancialMovement.eventId; migrar entonces sin cambiar lo que ve quien se inscribe. Las
-- instalaciones (M16) quedan como texto libre en "location"; el banner es una URL externa, no un archivo. Reusa AttendanceService (núcleo M09, contexto
-- EVENT) para el check-in y los componentes de QR del front (M09). El formulario público reusa el patrón de M07 (honeypot + límite por IP). El portal
-- del miembro (N4: mis eventos, inscribirme desde el portal) queda para M24, igual que en M06/M07/M09/M10/M11a/M11b/M12/M13.

INSERT INTO module (code, name_es, name_en, levels, kind, actions, delegable, route, icon, sort_order) VALUES
 ('EVENTS', 'Eventos', 'Events', ARRAY['N2','N3'], 'CONTRACTABLE', ARRAY['V','C','E','D','S','P','T','X','O'], TRUE, 'events', 'calendar', 290);

-- ---------------------------------------------------------------- evento
CREATE TABLE org_event (
    id                          UUID PRIMARY KEY,
    organization_id             UUID         NOT NULL REFERENCES organization (id),
    scope                       VARCHAR(12)  NOT NULL,
    branch_id                   UUID         REFERENCES branch (id),
    name                        VARCHAR(120) NOT NULL,
    type_code                   VARCHAR(40),
    description                 VARCHAR(2000),
    start_at                    TIMESTAMPTZ  NOT NULL,
    end_at                      TIMESTAMPTZ  NOT NULL,
    location                    VARCHAR(300),
    online_url                  VARCHAR(500),
    banner_url                  VARCHAR(500),
    capacity                    INT,
    waitlist_enabled            BOOLEAN      NOT NULL DEFAULT FALSE,
    reg_opens_at                TIMESTAMPTZ,
    reg_closes_at               TIMESTAMPTZ,
    cancel_deadline             TIMESTAMPTZ,
    is_public                   BOOLEAN      NOT NULL DEFAULT FALSE,
    requires_approval           BOOLEAN      NOT NULL DEFAULT FALSE,
    min_age                     INT,
    guests_max                  INT          NOT NULL DEFAULT 0,
    require_payment_for_checkin BOOLEAN      NOT NULL DEFAULT FALSE,
    status                      VARCHAR(10)  NOT NULL DEFAULT 'DRAFT',
    cancel_reason               VARCHAR(500),
    created_at                  TIMESTAMPTZ  NOT NULL,
    created_by                  UUID,
    updated_at                  TIMESTAMPTZ,
    updated_by                  UUID,
    version                     BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_event_scope CHECK (scope IN ('ORGANIZATION', 'BRANCH')),
    CONSTRAINT ck_event_scope_branch CHECK ((scope = 'BRANCH' AND branch_id IS NOT NULL) OR (scope = 'ORGANIZATION' AND branch_id IS NULL)),
    CONSTRAINT ck_event_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'FINISHED', 'CANCELLED')),
    CONSTRAINT ck_event_dates CHECK (end_at > start_at),                                                     -- [V2]
    CONSTRAINT ck_event_capacity CHECK (capacity IS NULL OR capacity >= 1),
    CONSTRAINT ck_event_guests_max CHECK (guests_max >= 0)
);
CREATE INDEX ix_event_org ON org_event (organization_id, status, start_at);
CREATE INDEX ix_event_branch ON org_event (branch_id, status, start_at);

-- ---------------------------------------------------------------- tarifa por categoría
CREATE TABLE event_price_tier (
    id        UUID PRIMARY KEY,
    event_id  UUID           NOT NULL REFERENCES org_event (id),
    category  VARCHAR(20)    NOT NULL,
    amount    NUMERIC(14, 2) NOT NULL,
    currency  VARCHAR(3)     NOT NULL DEFAULT 'PEN',
    CONSTRAINT ck_tier_category CHECK (category IN ('MEMBER', 'VISITOR', 'GUEST', 'SCHOLARSHIP', 'STAFF', 'TEMP_MEMBER', 'TEMP_STAFF')),
    CONSTRAINT ck_tier_amount CHECK (amount >= 0)                                                            -- [V6]
);
CREATE UNIQUE INDEX ux_tier_event_category ON event_price_tier (event_id, category);

-- ---------------------------------------------------------------- pregunta del formulario
CREATE TABLE event_question (
    id         UUID PRIMARY KEY,
    event_id   UUID         NOT NULL REFERENCES org_event (id),
    label      VARCHAR(200) NOT NULL,
    type       VARCHAR(10)  NOT NULL,
    options    VARCHAR(500),
    required   BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order INT          NOT NULL DEFAULT 0,
    CONSTRAINT ck_question_type CHECK (type IN ('TEXT', 'CHOICE', 'BOOL'))
);
CREATE INDEX ix_question_event ON event_question (event_id, sort_order);

-- ---------------------------------------------------------------- inscripción
CREATE TABLE event_registration (
    id                UUID           PRIMARY KEY,
    event_id          UUID           NOT NULL REFERENCES org_event (id),
    person_id         UUID           NOT NULL REFERENCES person (id),
    guardian_person_id UUID          REFERENCES person (id),                                                 -- [V11] tutor cuando inscribe a un menor
    category          VARCHAR(20)    NOT NULL,
    status            VARCHAR(12)    NOT NULL DEFAULT 'REGISTERED',
    payment_status    VARCHAR(10)    NOT NULL DEFAULT 'PENDING',
    amount            NUMERIC(14, 2) NOT NULL DEFAULT 0,                                                     -- [V8] copia de la tarifa al inscribirse
    currency          VARCHAR(3)     NOT NULL DEFAULT 'PEN',
    guests            INT            NOT NULL DEFAULT 0,
    answers           JSONB,
    ticket_code       VARCHAR(24),
    ticket_used_at    TIMESTAMPTZ,
    payment_method    VARCHAR(12),
    payment_reference VARCHAR(300),                                                                          -- referencia/comprobante de texto (provisional, ver cabecera)
    submitted_by      UUID,
    confirmed_by      UUID,
    confirmed_at      TIMESTAMPTZ,
    override_reason   VARCHAR(500),                                                                          -- [V13] inscripción fuera de ventana por el personal
    cancel_reason     VARCHAR(500),
    source            VARCHAR(10)    NOT NULL DEFAULT 'STAFF',
    registered_by     UUID,
    created_at        TIMESTAMPTZ    NOT NULL,
    created_by        UUID,
    updated_at        TIMESTAMPTZ,
    updated_by        UUID,
    version           BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT ck_reg_category CHECK (category IN ('MEMBER', 'VISITOR', 'GUEST', 'SCHOLARSHIP', 'STAFF', 'TEMP_MEMBER', 'TEMP_STAFF')),
    CONSTRAINT ck_reg_status CHECK (status IN ('REGISTERED', 'WAITLISTED', 'CANCELLED')),
    CONSTRAINT ck_reg_payment_status CHECK (payment_status IN ('PENDING', 'PAID', 'REFUNDED', 'WAIVED')),
    CONSTRAINT ck_reg_source CHECK (source IN ('STAFF', 'PUBLIC')),
    CONSTRAINT ck_reg_guests CHECK (guests >= 0)
);
CREATE UNIQUE INDEX ux_reg_active ON event_registration (event_id, person_id) WHERE status <> 'CANCELLED';   -- [V9]
CREATE UNIQUE INDEX ux_reg_ticket ON event_registration (ticket_code) WHERE ticket_code IS NOT NULL;
CREATE INDEX ix_reg_event ON event_registration (event_id, status);
CREATE INDEX ix_reg_person ON event_registration (person_id);
