-- V8 · M04 Sedes.
-- Amplía la sede (dirección, contacto, apertura, zona horaria propia, presentación pública, logo, horarios públicos)
-- y registra los módulos del catálogo que gestionan sedes.

ALTER TABLE branch
    ADD COLUMN address_line       VARCHAR(200),
    ADD COLUMN address_district   VARCHAR(80),
    ADD COLUMN address_city       VARCHAR(80),
    ADD COLUMN address_region     VARCHAR(80),
    ADD COLUMN address_country    VARCHAR(2),
    ADD COLUMN address_reference  VARCHAR(200),
    ADD COLUMN phone              VARCHAR(20),
    ADD COLUMN email              VARCHAR(160),
    ADD COLUMN opening_date       DATE,
    ADD COLUMN timezone           VARCHAR(50),          -- opcional: si es nulo se usa la de la organización
    ADD COLUMN display_name       VARCHAR(60),          -- nombre de presentación (certificados, correos, portal)
    ADD COLUMN logo_key           VARCHAR(255),
    ADD COLUMN logo_revision      INT NOT NULL DEFAULT 1,
    ADD COLUMN public_schedule    JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN inactivated_at     TIMESTAMPTZ;

ALTER TABLE branch ADD CONSTRAINT ck_branch_code    CHECK (code ~ '^[A-Z0-9-]{2,10}$');
ALTER TABLE branch ADD CONSTRAINT ck_branch_sched   CHECK (jsonb_typeof(public_schedule) = 'array');

-- Catálogo. BRANCHES = gestión de N1 (crear, editar, inactivar, cambiar principal);
-- BRANCH = "Sedes" de N2/N3: consulta y presentación de la sede (delegable a ORG_USER).
INSERT INTO module (code, name_es, name_en, levels, kind, actions, delegable, route, icon, sort_order) VALUES
 ('BRANCHES', 'Sedes', 'Branches', ARRAY['N1'],       'BASE', ARRAY['V','C','E','S'], FALSE, 'branches', 'environment', 105),
 ('BRANCH',   'Sedes', 'Branches', ARRAY['N2','N3'],  'BASE', ARRAY['V','E'],         TRUE,  'branches', 'environment', 105);
