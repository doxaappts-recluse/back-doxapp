-- V12 · M19 base: bandeja de notificaciones dentro de la aplicación (in-app). Correo, SMS, WhatsApp y campañas quedan fuera de esta versión.
-- owner_id = id del personal de plataforma (recipient_type STAFF, sin organización) o de la persona (recipient_type PERSON, con organización).
CREATE TABLE notification (
    id              UUID PRIMARY KEY,
    recipient_type  VARCHAR(6)   NOT NULL,
    owner_id        UUID         NOT NULL,
    organization_id UUID REFERENCES organization (id),
    type            VARCHAR(40)  NOT NULL,
    category        VARCHAR(15)  NOT NULL,
    params          JSONB        NOT NULL DEFAULT '{}'::jsonb,
    link            VARCHAR(200),
    dedupe_key      VARCHAR(120),
    created_at      TIMESTAMPTZ  NOT NULL,
    read_at         TIMESTAMPTZ,
    CONSTRAINT ck_notif_recipient CHECK (
        (recipient_type = 'STAFF'  AND organization_id IS NULL) OR
        (recipient_type = 'PERSON' AND organization_id IS NOT NULL)),
    CONSTRAINT ck_notif_category CHECK (category IN ('TRANSACTIONAL', 'COMMUNITY', 'EVENTS', 'FINANCE', 'PASTORAL'))
);
CREATE INDEX ix_notif_owner ON notification (owner_id, organization_id, created_at DESC);
CREATE INDEX ix_notif_unread ON notification (owner_id, read_at) WHERE read_at IS NULL;
-- Deduplicación: el mismo aviso (misma clave) no se crea dos veces para la misma persona.
CREATE UNIQUE INDEX ux_notif_dedupe ON notification (owner_id, dedupe_key) WHERE dedupe_key IS NOT NULL;
