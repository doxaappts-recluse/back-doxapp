-- V15 · M06 parte 2A · Privacidad, foto y exportación.
-- Consentimientos por persona (ConsentService) y foto de la persona (archivo en el almacenamiento, no en la base).

ALTER TABLE person ADD COLUMN photo_key        VARCHAR(300);
ALTER TABLE person ADD COLUMN photo_updated_at TIMESTAMPTZ;

CREATE TABLE consent_record (
    id              UUID PRIMARY KEY,
    organization_id UUID         NOT NULL REFERENCES organization (id),
    person_id       UUID         NOT NULL REFERENCES person (id),
    purpose_code    VARCHAR(30)  NOT NULL,
    version         VARCHAR(20)  NOT NULL,
    granted_at      TIMESTAMPTZ  NOT NULL,
    source          VARCHAR(20)  NOT NULL,
    granted_by      UUID,
    revoked_at      TIMESTAMPTZ,
    revoked_by      UUID,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_consent_purpose CHECK (purpose_code IN ('DATA_PROCESSING', 'COMMUNICATIONS', 'DIRECTORY')),
    CONSTRAINT ck_consent_source  CHECK (source IN ('STAFF', 'PUBLIC_FORM', 'PORTAL', 'IMPORT', 'VISITOR'))
);
CREATE INDEX ix_consent_person ON consent_record (person_id, purpose_code);
-- Solo un consentimiento vigente por persona y finalidad; el historial (revocados) se conserva.
CREATE UNIQUE INDEX ux_consent_valid ON consent_record (person_id, purpose_code) WHERE revoked_at IS NULL;

-- Consentimiento de las personas que ya tenían un caso de visitante con autorización (M07): el más antiguo por persona.
INSERT INTO consent_record (id, organization_id, person_id, purpose_code, version, granted_at, source, granted_by)
SELECT gen_random_uuid(), x.org, x.person, x.code, 'v1', x.at, 'VISITOR', NULL
  FROM (SELECT DISTINCT ON (c.person_id, p.code) c.organization_id AS org, c.person_id AS person, p.code AS code,
               coalesce(c.consent_at, c.created_at) AS at
          FROM visitor_case c CROSS JOIN (VALUES ('DATA_PROCESSING'), ('COMMUNICATIONS')) AS p(code)
         WHERE c.consent_status = 'GRANTED'
         ORDER BY c.person_id, p.code, coalesce(c.consent_at, c.created_at)) x;
