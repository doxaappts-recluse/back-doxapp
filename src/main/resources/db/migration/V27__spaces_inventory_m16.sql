-- V27 · M16 Instalaciones y recursos: dos módulos contratables — SPACES (espacios y reservas, con aprobación opcional reusando
-- ApprovalEngine tipo SPACE_RESERVATION) e INVENTORY (activos y consumibles: alta, movimientos, asignaciones, conteo físico,
-- traspaso entre sedes solo ORG_ADMIN, importación masiva reusando TabularReader/import_job de M06c). El tipo de espacio reusa
-- el catálogo SPACE_TYPE que M23 ya había registrado por adelantado (owner FACILITIES) en vez de crear ROOM_TYPE como dice el
-- spec al pie de la letra — mismo concepto, un solo catálogo. [D1] Las reservas creadas automáticamente por M14 (eventos), M10
-- (grupos) y M13 (dictados) mediante `sourceType/sourceId` NO se conectan todavía: location/aula siguen siendo texto libre en
-- esos tres módulos (como sus propios "cómo ejecutar" ya advertían); conectar esa integración queda para una entrega aparte,
-- igual que M15 dejó pendiente D5, para no tocar código ya validado. [D2] Compras de inventario con costo SÍ generan un egreso
-- PENDING en `fin_movement` (columna nueva `source_id`), solo cuando `FIN_MOVEMENTS` está contratado; se agrega `fin_movement.source_id`
-- porque V26 solo tenía `source_ref` (marca de origen) sin un identificador del registro origen — sin esa columna no había forma
-- de saber "ya generé el egreso de este movimiento de inventario" y evitar duplicarlo al reintentar [M16-T12].

INSERT INTO module (code, name_es, name_en, levels, kind, actions, delegable, route, icon, sort_order) VALUES
 ('SPACES', 'Espacios', 'Spaces', ARRAY['N2','N3'], 'CONTRACTABLE', ARRAY['V','C','E','D','S','A','X'], TRUE, 'spaces', 'calendar', 310),
 ('INVENTORY', 'Inventario', 'Inventory', ARRAY['N2','N3'], 'CONTRACTABLE', ARRAY['V','C','E','D','S','X','T','I'], TRUE, 'inventory', 'box', 320);

-- ---------------------------------------------------------------- espacios
CREATE TABLE space (
    id                UUID PRIMARY KEY,
    organization_id   UUID         NOT NULL REFERENCES organization (id),
    branch_id         UUID         NOT NULL REFERENCES branch (id),
    name              VARCHAR(120) NOT NULL,
    type_code         VARCHAR(40),                                                                              -- catálogo SPACE_TYPE
    capacity          INT,
    equipment         JSONB        NOT NULL DEFAULT '[]'::jsonb,
    requires_approval BOOLEAN      NOT NULL DEFAULT FALSE,
    open_from         TIME,
    open_to           TIME,
    buffer_minutes    INT          NOT NULL DEFAULT 0,
    status            VARCHAR(12)  NOT NULL DEFAULT 'ACTIVE',
    created_at        TIMESTAMPTZ  NOT NULL,
    created_by        UUID,
    updated_at        TIMESTAMPTZ,
    updated_by        UUID,
    version           BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_space_status CHECK (status IN ('ACTIVE', 'MAINTENANCE', 'INACTIVE')),
    CONSTRAINT ck_space_capacity CHECK (capacity IS NULL OR capacity >= 1),                                      -- [V3]
    CONSTRAINT ck_space_buffer CHECK (buffer_minutes >= 0)
);
CREATE UNIQUE INDEX ux_space_branch_name ON space (branch_id, lower(name));                                     -- [V3]
CREATE INDEX ix_space_org_branch ON space (organization_id, branch_id, status);

CREATE TABLE space_rules (
    id                     UUID PRIMARY KEY,
    organization_id        UUID        NOT NULL UNIQUE REFERENCES organization (id),
    max_duration_hours     INT         NOT NULL DEFAULT 12,
    reservation_requesters VARCHAR(12) NOT NULL DEFAULT 'STAFF',
    max_recurrence         INT         NOT NULL DEFAULT 12,
    updated_at             TIMESTAMPTZ,
    updated_by             UUID,
    CONSTRAINT ck_rules_duration CHECK (max_duration_hours >= 1),                                                -- [V1]
    CONSTRAINT ck_rules_requesters CHECK (reservation_requesters IN ('LEADERS', 'ANY_MEMBER', 'STAFF')),
    CONSTRAINT ck_rules_recurrence CHECK (max_recurrence >= 1)                                                   -- [V1]
);

-- ---------------------------------------------------------------- reservas
CREATE TABLE reservation (
    id                   UUID PRIMARY KEY,
    organization_id      UUID         NOT NULL REFERENCES organization (id),
    space_id             UUID         NOT NULL REFERENCES space (id),
    requested_by         UUID         NOT NULL REFERENCES person (id),
    title                VARCHAR(150) NOT NULL,
    source_type          VARCHAR(20)  NOT NULL DEFAULT 'OTHER',
    source_id            UUID,
    start_at             TIMESTAMPTZ  NOT NULL,
    end_at               TIMESTAMPTZ  NOT NULL,
    recurrence_group_id  UUID,
    attendees_est        INT,
    status               VARCHAR(10)  NOT NULL DEFAULT 'PENDING',
    decision_reason      VARCHAR(500),
    approval_request_id  UUID,
    created_at           TIMESTAMPTZ  NOT NULL,
    created_by           UUID,
    updated_at           TIMESTAMPTZ,
    updated_by           UUID,
    version              BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_reservation_status CHECK (status IN ('PENDING', 'CONFIRMED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT ck_reservation_source CHECK (source_type IN ('EVENT', 'SMALL_GROUP', 'BIBLE_CLASS', 'OTHER')),
    CONSTRAINT ck_reservation_dates CHECK (end_at > start_at)                                                    -- [V4]
);
CREATE INDEX ix_reservation_space_window ON reservation (space_id, status, start_at, end_at);                    -- [V5] solape
CREATE INDEX ix_reservation_requester ON reservation (requested_by);
CREATE INDEX ix_reservation_org ON reservation (organization_id, status);
CREATE INDEX ix_reservation_recurrence ON reservation (recurrence_group_id) WHERE recurrence_group_id IS NOT NULL;

-- ---------------------------------------------------------------- inventario
CREATE TABLE inventory_item (
    id                    UUID           PRIMARY KEY,
    organization_id       UUID           NOT NULL REFERENCES organization (id),
    branch_id             UUID           NOT NULL REFERENCES branch (id),
    code                  VARCHAR(40)    NOT NULL,
    name                  VARCHAR(150)   NOT NULL,
    category_code         VARCHAR(40),                                                                          -- catálogo INVENTORY_CATEGORY
    kind                  VARCHAR(12)    NOT NULL,
    unit                  VARCHAR(20)    NOT NULL DEFAULT 'UNIDAD',
    quantity              NUMERIC(12, 2) NOT NULL DEFAULT 0,
    min_stock             NUMERIC(12, 2),
    location              VARCHAR(150),
    acquisition_date      DATE,
    cost                  NUMERIC(14, 2),
    currency              VARCHAR(3)     NOT NULL DEFAULT 'PEN',
    condition             VARCHAR(10),
    serial_no             VARCHAR(80),
    photo_key             VARCHAR(300),
    status                VARCHAR(10)    NOT NULL DEFAULT 'ACTIVE',
    low_stock_alerted_at  TIMESTAMPTZ,                                                                          -- [V15] alerta única al cruzar
    created_at            TIMESTAMPTZ    NOT NULL,
    created_by            UUID,
    updated_at            TIMESTAMPTZ,
    updated_by            UUID,
    version               BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT ck_item_kind CHECK (kind IN ('ASSET', 'CONSUMABLE')),
    CONSTRAINT ck_item_condition CHECK (condition IS NULL OR condition IN ('NEW', 'GOOD', 'FAIR', 'POOR', 'BROKEN', 'RETIRED')),
    CONSTRAINT ck_item_status CHECK (status IN ('ACTIVE', 'RETIRED')),
    CONSTRAINT ck_item_quantity CHECK (quantity >= 0),                                                           -- [V10]
    CONSTRAINT ck_item_cost CHECK (cost IS NULL OR cost >= 0)                                                    -- [V12]
);
CREATE UNIQUE INDEX ux_item_branch_code ON inventory_item (branch_id, code);                                    -- [V9]
CREATE INDEX ix_item_org_branch ON inventory_item (organization_id, branch_id, status);

CREATE TABLE inventory_movement (
    id                     UUID           PRIMARY KEY,
    organization_id        UUID           NOT NULL REFERENCES organization (id),
    item_id                UUID           NOT NULL REFERENCES inventory_item (id),
    type                   VARCHAR(4)     NOT NULL,
    reason                 VARCHAR(20)    NOT NULL,
    quantity               NUMERIC(12, 2) NOT NULL,
    movement_date          DATE           NOT NULL,
    unit_cost              NUMERIC(14, 2),
    financial_movement_id  UUID,                                                                                -- fin_movement.id, sin FK dura (FIN_MOVEMENTS puede no estar contratado)
    transfer_group_id      UUID,                                                                                -- enlaza el OUT+IN de un traspaso [V2]
    note                   VARCHAR(300),
    created_at             TIMESTAMPTZ    NOT NULL,
    created_by             UUID,
    CONSTRAINT ck_movement_type CHECK (type IN ('IN', 'OUT')),
    CONSTRAINT ck_movement_reason CHECK (reason IN ('PURCHASE', 'DONATION', 'CONSUMPTION', 'LOSS', 'ADJUSTMENT', 'TRANSFER')),
    CONSTRAINT ck_movement_qty CHECK (quantity > 0)
);
CREATE INDEX ix_inv_movement_item ON inventory_movement (item_id, movement_date DESC);
CREATE INDEX ix_inv_movement_transfer ON inventory_movement (transfer_group_id) WHERE transfer_group_id IS NOT NULL;

CREATE TABLE inventory_assignment (
    id                     UUID        PRIMARY KEY,
    organization_id        UUID        NOT NULL REFERENCES organization (id),
    item_id                UUID        NOT NULL REFERENCES inventory_item (id),
    assignee_type          VARCHAR(10) NOT NULL,
    assignee_person_id     UUID        REFERENCES person (id),
    assignee_ministry_id   UUID        REFERENCES ministry (id),
    assignee_space_id      UUID        REFERENCES space (id),
    assigned_date          DATE        NOT NULL,
    expected_return        DATE,
    returned_date          DATE,
    return_condition       VARCHAR(10),
    status                 VARCHAR(10) NOT NULL DEFAULT 'ASSIGNED',
    overdue_alerted_at     TIMESTAMPTZ,
    created_at             TIMESTAMPTZ NOT NULL,
    created_by             UUID,
    updated_at             TIMESTAMPTZ,
    updated_by             UUID,
    version                BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_assignment_type CHECK (assignee_type IN ('PERSON', 'MINISTRY', 'SPACE')),
    CONSTRAINT ck_assignment_status CHECK (status IN ('ASSIGNED', 'RETURNED', 'OVERDUE', 'LOST')),
    CONSTRAINT ck_assignment_return_condition CHECK (return_condition IS NULL OR return_condition IN ('NEW', 'GOOD', 'FAIR', 'POOR', 'BROKEN', 'RETIRED')),
    CONSTRAINT ck_assignment_one_assignee CHECK (num_nonnulls(assignee_person_id, assignee_ministry_id, assignee_space_id) = 1),
    CONSTRAINT ck_assignment_return_date CHECK (returned_date IS NULL OR returned_date >= assigned_date),        -- [V11]
    CONSTRAINT ck_assignment_expected CHECK (expected_return IS NULL OR expected_return >= assigned_date)
);
CREATE UNIQUE INDEX ux_assignment_active ON inventory_assignment (item_id) WHERE status IN ('ASSIGNED', 'OVERDUE');  -- [V11] 1 activa por activo
CREATE INDEX ix_assignment_person ON inventory_assignment (assignee_person_id) WHERE assignee_person_id IS NOT NULL;

CREATE TABLE inventory_count (
    id               UUID        PRIMARY KEY,
    organization_id  UUID        NOT NULL REFERENCES organization (id),
    branch_id        UUID        NOT NULL REFERENCES branch (id),
    count_date       DATE        NOT NULL,
    status           VARCHAR(10) NOT NULL DEFAULT 'DRAFT',
    closed_at        TIMESTAMPTZ,
    closed_by        UUID,
    created_at       TIMESTAMPTZ NOT NULL,
    created_by       UUID,
    updated_at       TIMESTAMPTZ,
    updated_by       UUID,
    version          BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_count_status CHECK (status IN ('DRAFT', 'CLOSED'))
);
CREATE INDEX ix_count_branch ON inventory_count (branch_id, count_date DESC);

CREATE TABLE inventory_count_line (
    id        UUID           PRIMARY KEY,
    count_id  UUID           NOT NULL REFERENCES inventory_count (id),
    item_id   UUID           NOT NULL REFERENCES inventory_item (id),
    expected  NUMERIC(12, 2) NOT NULL,
    counted   NUMERIC(12, 2),
    adjusted  BOOLEAN        NOT NULL DEFAULT FALSE,
    note      VARCHAR(300),
    CONSTRAINT ux_count_line UNIQUE (count_id, item_id)
);

-- ---------------------------------------------------------------- integración con M15 (D2)
ALTER TABLE fin_movement ADD COLUMN source_id UUID;
CREATE UNIQUE INDEX ux_movement_source ON fin_movement (source_ref, source_id) WHERE source_ref IS NOT NULL AND source_id IS NOT NULL;  -- idempotencia [M16-T12]

-- ---------------------------------------------------------------- catálogo (M23): INVENTORY_CATEGORY nuevo; SPACE_TYPE ya existía
-- (CatalogTypeRegistry se actualiza en código; aquí no hay tabla de "tipos", solo los ítems del catálogo que cree cada organización)
