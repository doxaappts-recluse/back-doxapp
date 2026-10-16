-- V16 · M06 parte 2B · Fusión, anonimización e importación de personas.

-- Persona anonimizada: se conservan los agregados (sede, sexo, año de nacimiento, fechas) y se borra la identidad.
ALTER TABLE person ADD COLUMN anonymized_at TIMESTAMPTZ;
CREATE INDEX ix_person_anonymized ON person (organization_id) WHERE anonymized_at IS NOT NULL;

-- Trabajos de importación por archivo (personas hoy; finanzas y recursos reutilizan la tabla). El archivo se valida y guarda
-- normalizado en 'rows' para poder previsualizar y luego confirmar sin volver a subirlo.
CREATE TABLE import_job (
    id              UUID PRIMARY KEY,
    organization_id UUID         NOT NULL REFERENCES organization (id),
    kind            VARCHAR(30)  NOT NULL,
    file_name       VARCHAR(200) NOT NULL,
    file_hash       VARCHAR(64)  NOT NULL,
    options         JSONB        NOT NULL DEFAULT '{}'::jsonb,
    rows            JSONB        NOT NULL DEFAULT '[]'::jsonb,
    summary         JSONB        NOT NULL DEFAULT '{}'::jsonb,
    status          VARCHAR(15)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    created_by      UUID,
    confirmed_at    TIMESTAMPTZ,
    CONSTRAINT ck_import_job_status CHECK (status IN ('PREVIEW', 'DONE'))
);
CREATE INDEX ix_import_job_org ON import_job (organization_id, kind, created_at DESC);
