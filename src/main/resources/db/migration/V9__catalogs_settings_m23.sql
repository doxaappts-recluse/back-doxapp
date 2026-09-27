-- V9 · M23 Catálogos y configuración.
-- Catálogos (BASE por plataforma + ORG por organización), ajustes por módulo con herencia sede → organización → plataforma → default,
-- ajustes de plataforma, integraciones con secretos cifrados y ampliación del perfil de la organización.

-- ---------------------------------------------------------------- catálogos
CREATE TABLE catalog_item (
    id               UUID PRIMARY KEY,
    organization_id  UUID REFERENCES organization (id),
    type             VARCHAR(40)  NOT NULL,
    code             VARCHAR(60)  NOT NULL,
    name_es          VARCHAR(120) NOT NULL,
    name_en          VARCHAR(120) NOT NULL,
    sort_order       INT          NOT NULL DEFAULT 0,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    source           VARCHAR(10)  NOT NULL,
    parent_id        UUID REFERENCES catalog_item (id),
    meta             JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at       TIMESTAMPTZ  NOT NULL,
    created_by       UUID,
    updated_at       TIMESTAMPTZ,
    updated_by       UUID,
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_ci_code   CHECK (code ~ '^[A-Z0-9_]{2,60}$'),
    CONSTRAINT ck_ci_source CHECK ((source = 'BASE' AND organization_id IS NULL) OR (source = 'ORG' AND organization_id IS NOT NULL)),
    CONSTRAINT ck_ci_meta   CHECK (jsonb_typeof(meta) = 'object')
);
-- [V5] código único por (organización, tipo); los BASE (organización nula) son únicos entre sí.
CREATE UNIQUE INDEX ux_ci_code ON catalog_item (COALESCE(organization_id, '00000000-0000-0000-0000-000000000000'::uuid), type, code);
CREATE INDEX ix_ci_type   ON catalog_item (type, source, sort_order);
CREATE INDEX ix_ci_org    ON catalog_item (organization_id, type);
CREATE INDEX ix_ci_parent ON catalog_item (parent_id);

-- Ítems BASE que una organización decide ocultar en sus listas.
CREATE TABLE catalog_item_hidden (
    organization_id  UUID NOT NULL REFERENCES organization (id),
    item_id          UUID NOT NULL REFERENCES catalog_item (id),
    hidden_at        TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (organization_id, item_id)
);

-- ---------------------------------------------------------------- ajustes
CREATE TABLE org_setting (
    id               UUID PRIMARY KEY,
    organization_id  UUID NOT NULL REFERENCES organization (id),
    branch_id        UUID REFERENCES branch (id),
    namespace        VARCHAR(40) NOT NULL,
    setting_key      VARCHAR(60) NOT NULL,
    value            JSONB       NOT NULL,
    schema_version   INT         NOT NULL DEFAULT 1,
    created_at       TIMESTAMPTZ NOT NULL,
    created_by       UUID,
    updated_at       TIMESTAMPTZ,
    updated_by       UUID,
    version          BIGINT      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_os_key ON org_setting (organization_id, COALESCE(branch_id, '00000000-0000-0000-0000-000000000000'::uuid), namespace, setting_key);

CREATE TABLE platform_setting (
    setting_key   VARCHAR(100) PRIMARY KEY,
    value         JSONB       NOT NULL,
    description   VARCHAR(255),
    created_at    TIMESTAMPTZ NOT NULL,
    created_by    UUID,
    updated_at    TIMESTAMPTZ,
    updated_by    UUID,
    version       BIGINT      NOT NULL DEFAULT 0
);

-- ---------------------------------------------------------------- integraciones
CREATE TABLE integration_config (
    id                    UUID PRIMARY KEY,
    organization_id       UUID        NOT NULL REFERENCES organization (id),
    provider              VARCHAR(20) NOT NULL,
    kind                  VARCHAR(20) NOT NULL,
    settings              JSONB       NOT NULL DEFAULT '{}'::jsonb,
    secrets_enc           TEXT,
    status                VARCHAR(20) NOT NULL DEFAULT 'TESTING',
    last_test_at          TIMESTAMPTZ,
    last_test_ok          BOOLEAN,
    last_error            VARCHAR(500),
    consecutive_failures  INT         NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ NOT NULL,
    created_by            UUID,
    updated_at            TIMESTAMPTZ,
    updated_by            UUID,
    version               BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_ic_provider CHECK (provider IN ('EMAIL_SMTP','EMAIL_API','SMS','WHATSAPP','PAYMENT','STORAGE','CALENDAR','VIDEO')),
    CONSTRAINT ck_ic_status   CHECK (status IN ('UNCONFIGURED','TESTING','ACTIVE','ERROR')),
    CONSTRAINT ux_ic_provider UNIQUE (organization_id, provider)
);
-- [V9] una integración ACTIVE por (organización, tipo)
CREATE UNIQUE INDEX ux_ic_active_kind ON integration_config (organization_id, kind) WHERE status = 'ACTIVE';

-- ---------------------------------------------------------------- perfil de la organización
ALTER TABLE organization
    ADD COLUMN date_format   VARCHAR(12) NOT NULL DEFAULT 'dd/MM/yyyy',
    ADD COLUMN working_days  VARCHAR(30) NOT NULL DEFAULT 'MON,TUE,WED,THU,FRI';
ALTER TABLE organization ADD CONSTRAINT ck_org_datefmt CHECK (date_format IN ('dd/MM/yyyy','MM/dd/yyyy','yyyy-MM-dd'));

CREATE TABLE org_holiday (
    id               UUID PRIMARY KEY,
    organization_id  UUID         NOT NULL REFERENCES organization (id),
    holiday_date     DATE         NOT NULL,
    name             VARCHAR(100) NOT NULL,
    CONSTRAINT ux_holiday UNIQUE (organization_id, holiday_date)
);

-- ---------------------------------------------------------------- módulos
INSERT INTO module (code, name_es, name_en, levels, kind, actions, delegable, route, icon, sort_order) VALUES
 ('CATALOGS',          'Catálogos',               'Catalogs',           ARRAY['N1','N2','N3'], 'BASE',        ARRAY['V','C','E','S'], FALSE, 'catalogs',          'unordered-list', 300),
 ('ORG_SETTINGS',      'Configuración',           'Settings',           ARRAY['N2','N3'],      'BASE',        ARRAY['V','E'],         FALSE, 'settings',          'setting',        310),
 ('INTEGRATIONS',      'Integraciones',           'Integrations',       ARRAY['N2'],           'CONTRACTABLE', ARRAY['V','C','E','S'], FALSE, 'integrations',      'api',            320),
 ('PLATFORM_SETTINGS', 'Ajustes de plataforma',   'Platform settings',  ARRAY['N1'],           'BASE',        ARRAY['V','E'],         FALSE, 'platform-settings', 'control',        330);

-- ---------------------------------------------------------------- catálogos BASE

INSERT INTO catalog_item (id, organization_id, type, code, name_es, name_en, sort_order, active, source, parent_id, created_at) VALUES
 (gen_random_uuid(), NULL, 'DOCUMENT_TYPE', 'DNI', 'DNI', 'National ID (DNI)', 10, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'DOCUMENT_TYPE', 'CE', 'Carné de extranjería', 'Foreigner ID card', 20, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'DOCUMENT_TYPE', 'PASSPORT', 'Pasaporte', 'Passport', 30, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'DOCUMENT_TYPE', 'RUC', 'RUC', 'Tax ID (RUC)', 40, TRUE, 'BASE', NULL, now());

INSERT INTO catalog_item (id, organization_id, type, code, name_es, name_en, sort_order, active, source, parent_id, created_at) VALUES
 (gen_random_uuid(), NULL, 'GENDER', 'MALE', 'Masculino', 'Male', 10, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'GENDER', 'FEMALE', 'Femenino', 'Female', 20, TRUE, 'BASE', NULL, now());

INSERT INTO catalog_item (id, organization_id, type, code, name_es, name_en, sort_order, active, source, parent_id, created_at) VALUES
 (gen_random_uuid(), NULL, 'MARITAL_STATUS', 'SINGLE', 'Soltero/a', 'Single', 10, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'MARITAL_STATUS', 'MARRIED', 'Casado/a', 'Married', 20, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'MARITAL_STATUS', 'COHABITING', 'Conviviente', 'Cohabiting', 30, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'MARITAL_STATUS', 'DIVORCED', 'Divorciado/a', 'Divorced', 40, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'MARITAL_STATUS', 'WIDOWED', 'Viudo/a', 'Widowed', 50, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'MARITAL_STATUS', 'SEPARATED', 'Separado/a', 'Separated', 60, TRUE, 'BASE', NULL, now());

INSERT INTO catalog_item (id, organization_id, type, code, name_es, name_en, sort_order, active, source, parent_id, created_at) VALUES
 (gen_random_uuid(), NULL, 'RELATIONSHIP', 'FATHER', 'Padre', 'Father', 10, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'RELATIONSHIP', 'MOTHER', 'Madre', 'Mother', 20, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'RELATIONSHIP', 'SON', 'Hijo', 'Son', 30, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'RELATIONSHIP', 'DAUGHTER', 'Hija', 'Daughter', 40, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'RELATIONSHIP', 'SPOUSE', 'Cónyuge', 'Spouse', 50, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'RELATIONSHIP', 'SIBLING', 'Hermano/a', 'Sibling', 60, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'RELATIONSHIP', 'GRANDPARENT', 'Abuelo/a', 'Grandparent', 70, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'RELATIONSHIP', 'GRANDCHILD', 'Nieto/a', 'Grandchild', 80, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'RELATIONSHIP', 'UNCLE_AUNT', 'Tío/a', 'Uncle/Aunt', 90, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'RELATIONSHIP', 'COUSIN', 'Primo/a', 'Cousin', 100, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'RELATIONSHIP', 'GUARDIAN', 'Tutor/a', 'Guardian', 110, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'RELATIONSHIP', 'OTHER', 'Otro', 'Other', 120, TRUE, 'BASE', NULL, now());

INSERT INTO catalog_item (id, organization_id, type, code, name_es, name_en, sort_order, active, source, parent_id, created_at) VALUES
 (gen_random_uuid(), NULL, 'RITE_TYPE', 'BAPTISM', 'Bautismo', 'Baptism', 10, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'RITE_TYPE', 'MARRIAGE', 'Matrimonio', 'Marriage', 20, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'RITE_TYPE', 'CHILD_DEDICATION', 'Presentación de niños', 'Child dedication', 30, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'RITE_TYPE', 'MEMBERSHIP', 'Recepción como miembro', 'Membership reception', 40, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'RITE_TYPE', 'FUNERAL', 'Servicio fúnebre', 'Funeral service', 50, TRUE, 'BASE', NULL, now());

INSERT INTO catalog_item (id, organization_id, type, code, name_es, name_en, sort_order, active, source, parent_id, created_at) VALUES
 (gen_random_uuid(), NULL, 'FINANCE_CATEGORY', 'TITHE', 'Diezmo', 'Tithe', 10, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'FINANCE_CATEGORY', 'OFFERING', 'Ofrenda', 'Offering', 20, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'FINANCE_CATEGORY', 'MISSIONS', 'Misiones', 'Missions', 30, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'FINANCE_CATEGORY', 'DONATION', 'Donación', 'Donation', 40, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'FINANCE_CATEGORY', 'BUILDING', 'Construcción', 'Building fund', 50, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'FINANCE_CATEGORY', 'UTILITIES', 'Servicios básicos', 'Utilities', 60, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'FINANCE_CATEGORY', 'SALARIES', 'Remuneraciones', 'Salaries', 70, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'FINANCE_CATEGORY', 'MAINTENANCE', 'Mantenimiento', 'Maintenance', 80, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'FINANCE_CATEGORY', 'EVENTS', 'Eventos', 'Events', 90, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'FINANCE_CATEGORY', 'OTHER', 'Otros', 'Other', 100, TRUE, 'BASE', NULL, now());

INSERT INTO catalog_item (id, organization_id, type, code, name_es, name_en, sort_order, active, source, parent_id, created_at) VALUES
 (gen_random_uuid(), NULL, 'PAYMENT_METHOD', 'CASH', 'Efectivo', 'Cash', 10, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'PAYMENT_METHOD', 'TRANSFER', 'Transferencia', 'Bank transfer', 20, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'PAYMENT_METHOD', 'CARD', 'Tarjeta', 'Card', 30, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'PAYMENT_METHOD', 'DEPOSIT', 'Depósito', 'Deposit', 40, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'PAYMENT_METHOD', 'WALLET', 'Yape / Plin', 'Mobile wallet (Yape / Plin)', 50, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'PAYMENT_METHOD', 'CHECK', 'Cheque', 'Check', 60, TRUE, 'BASE', NULL, now());

INSERT INTO catalog_item (id, organization_id, type, code, name_es, name_en, sort_order, active, source, parent_id, created_at) VALUES
 (gen_random_uuid(), NULL, 'SPACE_TYPE', 'SANCTUARY', 'Templo', 'Sanctuary', 10, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'SPACE_TYPE', 'CLASSROOM', 'Aula', 'Classroom', 20, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'SPACE_TYPE', 'HALL', 'Salón', 'Hall', 30, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'SPACE_TYPE', 'OFFICE', 'Oficina', 'Office', 40, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'SPACE_TYPE', 'KITCHEN', 'Cocina', 'Kitchen', 50, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'SPACE_TYPE', 'SOUND_EQUIPMENT', 'Equipo de sonido', 'Sound equipment', 60, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'SPACE_TYPE', 'PROJECTOR', 'Proyector', 'Projector', 70, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'SPACE_TYPE', 'VEHICLE', 'Vehículo', 'Vehicle', 80, TRUE, 'BASE', NULL, now());

INSERT INTO catalog_item (id, organization_id, type, code, name_es, name_en, sort_order, active, source, parent_id, created_at) VALUES
 (gen_random_uuid(), NULL, 'GROUP_TYPE', 'CELL', 'Célula', 'Cell group', 10, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'GROUP_TYPE', 'YOUTH', 'Jóvenes', 'Youth', 20, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'GROUP_TYPE', 'WOMEN', 'Mujeres', 'Women', 30, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'GROUP_TYPE', 'MEN', 'Varones', 'Men', 40, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'GROUP_TYPE', 'CHILDREN', 'Niños', 'Children', 50, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'GROUP_TYPE', 'SENIORS', 'Adultos mayores', 'Seniors', 60, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'GROUP_TYPE', 'PRAYER', 'Oración', 'Prayer', 70, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'GROUP_TYPE', 'STUDY', 'Estudio bíblico', 'Bible study', 80, TRUE, 'BASE', NULL, now());

INSERT INTO catalog_item (id, organization_id, type, code, name_es, name_en, sort_order, active, source, parent_id, created_at) VALUES
 (gen_random_uuid(), NULL, 'EXIT_REASON', 'MOVED', 'Cambio de residencia', 'Moved away', 10, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'EXIT_REASON', 'TRANSFERRED', 'Traslado a otra iglesia', 'Transferred to another church', 20, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'EXIT_REASON', 'PERSONAL', 'Motivos personales', 'Personal reasons', 30, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'EXIT_REASON', 'DECEASED', 'Fallecimiento', 'Deceased', 40, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'EXIT_REASON', 'INACTIVE', 'Inactividad prolongada', 'Prolonged inactivity', 50, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'EXIT_REASON', 'DISCIPLINE', 'Disciplina', 'Discipline', 60, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'EXIT_REASON', 'OTHER', 'Otro', 'Other', 70, TRUE, 'BASE', NULL, now());

INSERT INTO catalog_item (id, organization_id, type, code, name_es, name_en, sort_order, active, source, parent_id, created_at) VALUES
 (gen_random_uuid(), NULL, 'VISITOR_SOURCE', 'MEMBER_INVITATION', 'Invitación de un miembro', 'Invited by a member', 10, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'VISITOR_SOURCE', 'SOCIAL_MEDIA', 'Redes sociales', 'Social media', 20, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'VISITOR_SOURCE', 'WALK_IN', 'Pasó por la puerta', 'Walk-in', 30, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'VISITOR_SOURCE', 'EVENT', 'Evento', 'Event', 40, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'VISITOR_SOURCE', 'FLYER', 'Volante', 'Flyer', 50, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'VISITOR_SOURCE', 'WEBSITE', 'Sitio web', 'Website', 60, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'VISITOR_SOURCE', 'OTHER', 'Otro', 'Other', 70, TRUE, 'BASE', NULL, now());

INSERT INTO catalog_item (id, organization_id, type, code, name_es, name_en, sort_order, active, source, parent_id, created_at) VALUES
 (gen_random_uuid(), NULL, 'PASTORAL_CARE_TYPE', 'HOME_VISIT', 'Visita al hogar', 'Home visit', 10, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'PASTORAL_CARE_TYPE', 'HOSPITAL_VISIT', 'Visita al hospital', 'Hospital visit', 20, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'PASTORAL_CARE_TYPE', 'COUNSELING', 'Consejería', 'Counseling', 30, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'PASTORAL_CARE_TYPE', 'PRAYER', 'Oración', 'Prayer', 40, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'PASTORAL_CARE_TYPE', 'BEREAVEMENT', 'Acompañamiento en duelo', 'Bereavement support', 50, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'PASTORAL_CARE_TYPE', 'MARRIAGE_COUNSELING', 'Consejería matrimonial', 'Marriage counseling', 60, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'PASTORAL_CARE_TYPE', 'OTHER', 'Otro', 'Other', 70, TRUE, 'BASE', NULL, now());

INSERT INTO catalog_item (id, organization_id, type, code, name_es, name_en, sort_order, active, source, parent_id, created_at) VALUES
 (gen_random_uuid(), NULL, 'ABSENCE_TYPE', 'VACATION', 'Vacaciones', 'Vacation', 10, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'ABSENCE_TYPE', 'SICK_LEAVE', 'Descanso médico', 'Sick leave', 20, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'ABSENCE_TYPE', 'PERSONAL', 'Permiso personal', 'Personal leave', 30, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'ABSENCE_TYPE', 'MATERNITY', 'Licencia de maternidad', 'Maternity leave', 40, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'ABSENCE_TYPE', 'PATERNITY', 'Licencia de paternidad', 'Paternity leave', 50, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'ABSENCE_TYPE', 'UNPAID', 'Licencia sin goce', 'Unpaid leave', 60, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'ABSENCE_TYPE', 'OTHER', 'Otro', 'Other', 70, TRUE, 'BASE', NULL, now());

INSERT INTO catalog_item (id, organization_id, type, code, name_es, name_en, sort_order, active, source, parent_id, created_at) VALUES
 (gen_random_uuid(), NULL, 'LANGUAGE', 'ES', 'Español', 'Spanish', 10, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'LANGUAGE', 'EN', 'Inglés', 'English', 20, TRUE, 'BASE', NULL, now());

INSERT INTO catalog_item (id, organization_id, type, code, name_es, name_en, sort_order, active, source, parent_id, created_at) VALUES
 (gen_random_uuid(), NULL, 'PROFESSION', 'TEACHER', 'Docente', 'Teacher', 10, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'PROFESSION', 'ENGINEER', 'Ingeniero/a', 'Engineer', 20, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'PROFESSION', 'NURSE', 'Enfermero/a', 'Nurse', 30, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'PROFESSION', 'DOCTOR', 'Médico/a', 'Doctor', 40, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'PROFESSION', 'LAWYER', 'Abogado/a', 'Lawyer', 50, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'PROFESSION', 'ACCOUNTANT', 'Contador/a', 'Accountant', 60, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'PROFESSION', 'STUDENT', 'Estudiante', 'Student', 70, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'PROFESSION', 'MERCHANT', 'Comerciante', 'Merchant', 80, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'PROFESSION', 'HOMEMAKER', 'Ama de casa', 'Homemaker', 90, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'PROFESSION', 'OTHER', 'Otra', 'Other', 100, TRUE, 'BASE', NULL, now());

INSERT INTO catalog_item (id, organization_id, type, code, name_es, name_en, sort_order, active, source, parent_id, created_at) VALUES
 (gen_random_uuid(), NULL, 'COUNTRY', 'PE', 'Perú', 'Peru', 10, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'COUNTRY', 'AR', 'Argentina', 'Argentina', 20, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'COUNTRY', 'BO', 'Bolivia', 'Bolivia', 30, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'COUNTRY', 'BR', 'Brasil', 'Brazil', 40, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'COUNTRY', 'CL', 'Chile', 'Chile', 50, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'COUNTRY', 'CO', 'Colombia', 'Colombia', 60, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'COUNTRY', 'EC', 'Ecuador', 'Ecuador', 70, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'COUNTRY', 'MX', 'México', 'Mexico', 80, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'COUNTRY', 'PY', 'Paraguay', 'Paraguay', 90, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'COUNTRY', 'UY', 'Uruguay', 'Uruguay', 100, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'COUNTRY', 'VE', 'Venezuela', 'Venezuela', 110, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'COUNTRY', 'US', 'Estados Unidos', 'United States', 120, TRUE, 'BASE', NULL, now()),
 (gen_random_uuid(), NULL, 'COUNTRY', 'ES', 'España', 'Spain', 130, TRUE, 'BASE', NULL, now());

INSERT INTO catalog_item (id, organization_id, type, code, name_es, name_en, sort_order, active, source, parent_id, created_at) VALUES
 (gen_random_uuid(), NULL, 'REGION', 'PE_AMAZONAS', 'Amazonas', 'Amazonas', 10, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='COUNTRY' AND code='PE' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'REGION', 'PE_ANCASH', 'Áncash', 'Áncash', 20, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='COUNTRY' AND code='PE' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'REGION', 'PE_APURIMAC', 'Apurímac', 'Apurímac', 30, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='COUNTRY' AND code='PE' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'REGION', 'PE_AREQUIPA', 'Arequipa', 'Arequipa', 40, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='COUNTRY' AND code='PE' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'REGION', 'PE_AYACUCHO', 'Ayacucho', 'Ayacucho', 50, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='COUNTRY' AND code='PE' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'REGION', 'PE_CAJAMARCA', 'Cajamarca', 'Cajamarca', 60, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='COUNTRY' AND code='PE' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'REGION', 'PE_CALLAO', 'Callao', 'Callao', 70, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='COUNTRY' AND code='PE' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'REGION', 'PE_CUSCO', 'Cusco', 'Cusco', 80, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='COUNTRY' AND code='PE' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'REGION', 'PE_HUANCAVELICA', 'Huancavelica', 'Huancavelica', 90, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='COUNTRY' AND code='PE' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'REGION', 'PE_HUANUCO', 'Huánuco', 'Huánuco', 100, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='COUNTRY' AND code='PE' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'REGION', 'PE_ICA', 'Ica', 'Ica', 110, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='COUNTRY' AND code='PE' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'REGION', 'PE_JUNIN', 'Junín', 'Junín', 120, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='COUNTRY' AND code='PE' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'REGION', 'PE_LA_LIBERTAD', 'La Libertad', 'La Libertad', 130, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='COUNTRY' AND code='PE' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'REGION', 'PE_LAMBAYEQUE', 'Lambayeque', 'Lambayeque', 140, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='COUNTRY' AND code='PE' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'REGION', 'PE_LIMA', 'Lima', 'Lima', 150, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='COUNTRY' AND code='PE' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'REGION', 'PE_LORETO', 'Loreto', 'Loreto', 160, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='COUNTRY' AND code='PE' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'REGION', 'PE_MADRE_DE_DIOS', 'Madre de Dios', 'Madre de Dios', 170, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='COUNTRY' AND code='PE' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'REGION', 'PE_MOQUEGUA', 'Moquegua', 'Moquegua', 180, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='COUNTRY' AND code='PE' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'REGION', 'PE_PASCO', 'Pasco', 'Pasco', 190, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='COUNTRY' AND code='PE' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'REGION', 'PE_PIURA', 'Piura', 'Piura', 200, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='COUNTRY' AND code='PE' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'REGION', 'PE_PUNO', 'Puno', 'Puno', 210, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='COUNTRY' AND code='PE' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'REGION', 'PE_SAN_MARTIN', 'San Martín', 'San Martín', 220, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='COUNTRY' AND code='PE' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'REGION', 'PE_TACNA', 'Tacna', 'Tacna', 230, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='COUNTRY' AND code='PE' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'REGION', 'PE_TUMBES', 'Tumbes', 'Tumbes', 240, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='COUNTRY' AND code='PE' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'REGION', 'PE_UCAYALI', 'Ucayali', 'Ucayali', 250, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='COUNTRY' AND code='PE' AND organization_id IS NULL), now());

INSERT INTO catalog_item (id, organization_id, type, code, name_es, name_en, sort_order, active, source, parent_id, created_at) VALUES
 (gen_random_uuid(), NULL, 'DISTRICT', 'PE_LIMA_LIMA', 'Lima', 'Lima', 10, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='REGION' AND code='PE_LIMA' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'DISTRICT', 'PE_LIMA_ANCON', 'Ancón', 'Ancón', 20, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='REGION' AND code='PE_LIMA' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'DISTRICT', 'PE_LIMA_ATE', 'Ate', 'Ate', 30, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='REGION' AND code='PE_LIMA' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'DISTRICT', 'PE_LIMA_BARRANCO', 'Barranco', 'Barranco', 40, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='REGION' AND code='PE_LIMA' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'DISTRICT', 'PE_LIMA_BRENA', 'Breña', 'Breña', 50, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='REGION' AND code='PE_LIMA' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'DISTRICT', 'PE_LIMA_CARABAYLLO', 'Carabayllo', 'Carabayllo', 60, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='REGION' AND code='PE_LIMA' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'DISTRICT', 'PE_LIMA_CHACLACAYO', 'Chaclacayo', 'Chaclacayo', 70, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='REGION' AND code='PE_LIMA' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'DISTRICT', 'PE_LIMA_CHORRILLOS', 'Chorrillos', 'Chorrillos', 80, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='REGION' AND code='PE_LIMA' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'DISTRICT', 'PE_LIMA_CIENEGUILLA', 'Cieneguilla', 'Cieneguilla', 90, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='REGION' AND code='PE_LIMA' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'DISTRICT', 'PE_LIMA_COMAS', 'Comas', 'Comas', 100, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='REGION' AND code='PE_LIMA' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'DISTRICT', 'PE_LIMA_EL_AGUSTINO', 'El Agustino', 'El Agustino', 110, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='REGION' AND code='PE_LIMA' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'DISTRICT', 'PE_LIMA_INDEPENDENCIA', 'Independencia', 'Independencia', 120, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='REGION' AND code='PE_LIMA' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'DISTRICT', 'PE_LIMA_JESUS_MARIA', 'Jesús María', 'Jesús María', 130, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='REGION' AND code='PE_LIMA' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'DISTRICT', 'PE_LIMA_LA_MOLINA', 'La Molina', 'La Molina', 140, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='REGION' AND code='PE_LIMA' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'DISTRICT', 'PE_LIMA_LA_VICTORIA', 'La Victoria', 'La Victoria', 150, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='REGION' AND code='PE_LIMA' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'DISTRICT', 'PE_LIMA_LINCE', 'Lince', 'Lince', 160, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='REGION' AND code='PE_LIMA' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'DISTRICT', 'PE_LIMA_LOS_OLIVOS', 'Los Olivos', 'Los Olivos', 170, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='REGION' AND code='PE_LIMA' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'DISTRICT', 'PE_LIMA_LURIGANCHO_CHOSICA', 'Lurigancho-Chosica', 'Lurigancho-Chosica', 180, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='REGION' AND code='PE_LIMA' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'DISTRICT', 'PE_LIMA_LURIN', 'Lurín', 'Lurín', 190, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='REGION' AND code='PE_LIMA' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'DISTRICT', 'PE_LIMA_MAGDALENA_DEL_MAR', 'Magdalena del Mar', 'Magdalena del Mar', 200, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='REGION' AND code='PE_LIMA' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'DISTRICT', 'PE_LIMA_MIRAFLORES', 'Miraflores', 'Miraflores', 210, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='REGION' AND code='PE_LIMA' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'DISTRICT', 'PE_LIMA_PACHACAMAC', 'Pachacámac', 'Pachacámac', 220, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='REGION' AND code='PE_LIMA' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'DISTRICT', 'PE_LIMA_PUCUSANA', 'Pucusana', 'Pucusana', 230, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='REGION' AND code='PE_LIMA' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'DISTRICT', 'PE_LIMA_PUEBLO_LIBRE', 'Pueblo Libre', 'Pueblo Libre', 240, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='REGION' AND code='PE_LIMA' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'DISTRICT', 'PE_LIMA_PUENTE_PIEDRA', 'Puente Piedra', 'Puente Piedra', 250, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='REGION' AND code='PE_LIMA' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'DISTRICT', 'PE_LIMA_PUNTA_HERMOSA', 'Punta Hermosa', 'Punta Hermosa', 260, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='REGION' AND code='PE_LIMA' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'DISTRICT', 'PE_LIMA_PUNTA_NEGRA', 'Punta Negra', 'Punta Negra', 270, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='REGION' AND code='PE_LIMA' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'DISTRICT', 'PE_LIMA_RIMAC', 'Rímac', 'Rímac', 280, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='REGION' AND code='PE_LIMA' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'DISTRICT', 'PE_LIMA_SAN_BARTOLO', 'San Bartolo', 'San Bartolo', 290, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='REGION' AND code='PE_LIMA' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'DISTRICT', 'PE_LIMA_SAN_BORJA', 'San Borja', 'San Borja', 300, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='REGION' AND code='PE_LIMA' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'DISTRICT', 'PE_LIMA_SAN_ISIDRO', 'San Isidro', 'San Isidro', 310, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='REGION' AND code='PE_LIMA' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'DISTRICT', 'PE_LIMA_SAN_JUAN_DE_LURIGANCHO', 'San Juan de Lurigancho', 'San Juan de Lurigancho', 320, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='REGION' AND code='PE_LIMA' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'DISTRICT', 'PE_LIMA_SAN_JUAN_DE_MIRAFLORES', 'San Juan de Miraflores', 'San Juan de Miraflores', 330, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='REGION' AND code='PE_LIMA' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'DISTRICT', 'PE_LIMA_SAN_LUIS', 'San Luis', 'San Luis', 340, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='REGION' AND code='PE_LIMA' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'DISTRICT', 'PE_LIMA_SAN_MARTIN_DE_PORRES', 'San Martín de Porres', 'San Martín de Porres', 350, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='REGION' AND code='PE_LIMA' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'DISTRICT', 'PE_LIMA_SAN_MIGUEL', 'San Miguel', 'San Miguel', 360, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='REGION' AND code='PE_LIMA' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'DISTRICT', 'PE_LIMA_SANTA_ANITA', 'Santa Anita', 'Santa Anita', 370, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='REGION' AND code='PE_LIMA' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'DISTRICT', 'PE_LIMA_SANTA_MARIA_DEL_MAR', 'Santa María del Mar', 'Santa María del Mar', 380, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='REGION' AND code='PE_LIMA' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'DISTRICT', 'PE_LIMA_SANTA_ROSA', 'Santa Rosa', 'Santa Rosa', 390, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='REGION' AND code='PE_LIMA' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'DISTRICT', 'PE_LIMA_SANTIAGO_DE_SURCO', 'Santiago de Surco', 'Santiago de Surco', 400, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='REGION' AND code='PE_LIMA' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'DISTRICT', 'PE_LIMA_SURQUILLO', 'Surquillo', 'Surquillo', 410, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='REGION' AND code='PE_LIMA' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'DISTRICT', 'PE_LIMA_VILLA_EL_SALVADOR', 'Villa El Salvador', 'Villa El Salvador', 420, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='REGION' AND code='PE_LIMA' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'DISTRICT', 'PE_LIMA_VILLA_MARIA_DEL_TRIUNFO', 'Villa María del Triunfo', 'Villa María del Triunfo', 430, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='REGION' AND code='PE_LIMA' AND organization_id IS NULL), now());

INSERT INTO catalog_item (id, organization_id, type, code, name_es, name_en, sort_order, active, source, parent_id, created_at) VALUES
 (gen_random_uuid(), NULL, 'DISTRICT', 'PE_CALLAO_CALLAO', 'Callao', 'Callao', 10, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='REGION' AND code='PE_CALLAO' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'DISTRICT', 'PE_CALLAO_BELLAVISTA', 'Bellavista', 'Bellavista', 20, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='REGION' AND code='PE_CALLAO' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'DISTRICT', 'PE_CALLAO_CARMEN_DE_LA_LEGUA_REYNOSO', 'Carmen de la Legua Reynoso', 'Carmen de la Legua Reynoso', 30, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='REGION' AND code='PE_CALLAO' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'DISTRICT', 'PE_CALLAO_LA_PERLA', 'La Perla', 'La Perla', 40, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='REGION' AND code='PE_CALLAO' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'DISTRICT', 'PE_CALLAO_LA_PUNTA', 'La Punta', 'La Punta', 50, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='REGION' AND code='PE_CALLAO' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'DISTRICT', 'PE_CALLAO_MI_PERU', 'Mi Perú', 'Mi Perú', 60, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='REGION' AND code='PE_CALLAO' AND organization_id IS NULL), now()),
 (gen_random_uuid(), NULL, 'DISTRICT', 'PE_CALLAO_VENTANILLA', 'Ventanilla', 'Ventanilla', 70, TRUE, 'BASE', (SELECT id FROM catalog_item WHERE type='REGION' AND code='PE_CALLAO' AND organization_id IS NULL), now());
