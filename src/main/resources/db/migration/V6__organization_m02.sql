-- V6 · M02 Organizaciones e identidad de marca.
-- Amplía organization (dirección, ciclo de vida), agrega la marca 1:1, la redirección de slugs y los módulos del catálogo.

ALTER TABLE organization
    ADD COLUMN address_line       VARCHAR(200),
    ADD COLUMN address_district   VARCHAR(80),
    ADD COLUMN address_city       VARCHAR(80),
    ADD COLUMN address_region     VARCHAR(80),
    ADD COLUMN address_country    VARCHAR(2),
    ADD COLUMN address_reference  VARCHAR(200),
    ADD COLUMN status_reason      VARCHAR(255),
    ADD COLUMN activated_at       TIMESTAMPTZ,
    ADD COLUMN closed_at          TIMESTAMPTZ,
    ADD COLUMN retention_until    DATE;

-- [V3] el slug ya tiene CHECK y unique(lower(slug)) desde V1. Aquí solo se refuerza el formato de RUC y el país.
ALTER TABLE organization ADD CONSTRAINT ck_org_tax_id  CHECK (tax_id IS NULL OR tax_id ~ '^[0-9]{11}$');
ALTER TABLE organization ADD CONSTRAINT ck_org_country CHECK (country ~ '^[A-Z]{2}$');

-- Marca (1:1). Los archivos viven en FileStorageService; aquí solo la clave (org/{orgId}/branding/...).
CREATE TABLE organization_branding (
    organization_id     UUID PRIMARY KEY REFERENCES organization (id),
    display_name        VARCHAR(60),
    logo_light_key      VARCHAR(255),
    logo_dark_key       VARCHAR(255),
    favicon_key         VARCHAR(255),
    login_background_key VARCHAR(255),
    primary_color       VARCHAR(7),
    secondary_color     VARCHAR(7),
    welcome_text_es     VARCHAR(300),
    welcome_text_en     VARCHAR(300),
    contact_email       VARCHAR(160),
    contact_phone       VARCHAR(20),
    socials             JSONB,
    revision            INT          NOT NULL DEFAULT 1,
    created_at          TIMESTAMPTZ  NOT NULL,
    created_by          UUID,
    updated_at          TIMESTAMPTZ,
    updated_by          UUID,
    version             BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_brand_primary   CHECK (primary_color   IS NULL OR primary_color   ~ '^#[0-9A-F]{6}$'),
    CONSTRAINT ck_brand_secondary CHECK (secondary_color IS NULL OR secondary_color ~ '^#[0-9A-F]{6}$')
);

-- [V8] cambio de slug: el identificador anterior redirige 90 días al nuevo.
CREATE TABLE slug_redirect (
    old_slug         VARCHAR(30) PRIMARY KEY,
    organization_id  UUID        NOT NULL REFERENCES organization (id),
    until_at         TIMESTAMPTZ NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_redirect_slug CHECK (old_slug = lower(old_slug))
);
CREATE INDEX ix_slug_redirect_org ON slug_redirect (organization_id);

-- Catálogo de módulos. ORGANIZATIONS = gestión de N1; ORGANIZATION = "Mi organización" (N2 edita, N3 solo ve);
-- ORG_BRANDING = pestaña de marca (solo N2; sin entrada propia en el menú).
INSERT INTO module (code, name_es, name_en, levels, kind, actions, delegable, route, icon, sort_order) VALUES
 ('ORGANIZATIONS', 'Organizaciones',   'Organizations',    ARRAY['N1'],       'BASE', ARRAY['V','C','E','S'], FALSE, 'organizations', 'bank', 100),
 ('ORGANIZATION',  'Mi organización',  'My organization',  ARRAY['N2','N3'],  'BASE', ARRAY['V','E'],         FALSE, 'organization',  'home', 100),
 ('ORG_BRANDING',  'Marca',            'Branding',         ARRAY['N2'],       'BASE', ARRAY['V','E'],         FALSE, NULL,            NULL,   101);
