-- V29 · M18 Plantillas y certificados: motor general de plantillas visuales + emisión numerada con código de verificación y QR,
-- para los tipos de documento que ningún módulo anterior cubre todavía (EVENT_TICKET, EVENT_PARTICIPATION, DONATION_CERTIFICATE,
-- RECEIPT, PAYSLIP, EMPLOYMENT_LETTER, OTHER) y como base reutilizable de los tipos de certificado que M08/M13 ya emiten.
--
-- [D1] M08 (`certificate`, V19) y M13 (`training_certificate`, V24) YA guardan número, código de verificación y un snapshot de
-- datos — sus propios javadocs dicen literalmente "la plantilla y el PDF llegan con M18" / "propio hasta que M18 unifique
-- plantillas". Migrarlos a este motor (que generen aquí su PDF real en vez de su snapshot JSON) es una integración real pero
-- se deja pendiente de una entrega aparte, mismo motivo que D5 de M15 (M14/M08 sin postear a `fin_movement` todavía) y D1 de
-- M16 (reservas automáticas de M14/M10/M13 sin llamador todavía): tocar dos servicios ya validados y con certificados reales
-- emitidos en producción no es un cambio que deba entrar de paso en la misma entrega que construye el motor nuevo.
--
-- [D2] "Diseñador visual" (spec N2) se interpreta aquí como un editor de elementos posicionados (texto con variables `{{...}}`,
-- imagen, firma, código QR) sobre una página A4/Carta, no un lienzo de arrastrar-y-soltar: no hay ya en este proyecto una
-- librería de canvas y construir una es un proyecto en sí mismo. El resultado (una plantilla versionada con variables
-- validadas y vista previa) cumple el mismo objetivo del spec con el stack existente (Angular + ng-zorro, sin canvas nuevo).
--
-- [D3] Sin `NumberingService` compartido (confirmado que no existe en el código, igual que en M15/M16/M17): contador propio
-- `issued_document_counter` por (organización, tipo, año), mismo patrón UPSERT que `fin_receipt_counter`/`certificate_counter`.
--
-- Reusa sin tocarlo: `organization_branding`/`branch.logo_key` (M02/M04) para la marca del PDF; `FileStorageService` para
-- guardar imágenes de plantilla, firmas y el PDF emitido; los mismos patrones de límite de envíos por IP que ya usan M07
-- (`PublicRateLimiter`) y M14 (`EventPublicRateLimiter`) para `/public/v/{code}`.

INSERT INTO module (code, name_es, name_en, levels, kind, actions, delegable, route, icon, sort_order) VALUES
 ('DOC_TEMPLATES', 'Plantillas y certificados', 'Templates & certificates', ARRAY['N2','N3'], 'CONTRACTABLE',
  ARRAY['V','C','E','D','S','I','Y','X'], TRUE, 'document-templates', 'file-text', 340);

-- ---------------------------------------------------------------- plantillas base (N1, SYSTEM_ADMIN vía el módulo CATALOGS)
CREATE TABLE template_base (
    id          UUID PRIMARY KEY,
    type        VARCHAR(30)  NOT NULL,
    name        VARCHAR(120) NOT NULL,
    design      JSONB        NOT NULL,
    locale      VARCHAR(5)   NOT NULL DEFAULT 'es',
    version     INT          NOT NULL DEFAULT 1,
    status      VARCHAR(10)  NOT NULL DEFAULT 'DRAFT',
    created_at  TIMESTAMPTZ  NOT NULL,
    created_by  UUID,
    updated_at  TIMESTAMPTZ,
    updated_by  UUID,
    CONSTRAINT ck_tplbase_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);
-- [V2] una PUBLISHED por (tipo, idioma)
CREATE UNIQUE INDEX ux_tplbase_published ON template_base (type, locale) WHERE status = 'PUBLISHED';

-- ---------------------------------------------------------------- plantillas de la organización (N2)
CREATE TABLE document_template (
    id               UUID PRIMARY KEY,
    organization_id  UUID         NOT NULL REFERENCES organization (id),
    branch_id        UUID         REFERENCES branch (id),                                                        -- null = para toda la organización
    base_template_id UUID         REFERENCES template_base (id),                                                 -- origen si se copió de una base (trazabilidad, no obligatorio)
    type             VARCHAR(30)  NOT NULL,
    name             VARCHAR(120) NOT NULL,
    design           JSONB        NOT NULL,
    locale           VARCHAR(5)   NOT NULL DEFAULT 'es',
    version          INT          NOT NULL DEFAULT 1,
    status           VARCHAR(10)  NOT NULL DEFAULT 'DRAFT',
    is_default       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ  NOT NULL,
    created_by       UUID,
    updated_at       TIMESTAMPTZ,
    updated_by       UUID,
    version_lock     BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_doctpl_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);
-- [V7] un default por (tipo, sede) — NULL de branch_id se trata como una sede más gracias al coalesce a un UUID fijo
CREATE UNIQUE INDEX ux_doctpl_default ON document_template (organization_id, type, COALESCE(branch_id, '00000000-0000-0000-0000-000000000000'::uuid)) WHERE is_default AND status = 'PUBLISHED';
CREATE INDEX ix_doctpl_org_type ON document_template (organization_id, type, status);

-- ---------------------------------------------------------------- firmantes
CREATE TABLE signatory (
    id               UUID PRIMARY KEY,
    organization_id  UUID         NOT NULL REFERENCES organization (id),
    person_id        UUID         NOT NULL REFERENCES person (id),
    title            VARCHAR(120) NOT NULL,
    signature_key    VARCHAR(255),
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ  NOT NULL,
    created_by       UUID,
    updated_at       TIMESTAMPTZ,
    updated_by       UUID,
    version          BIGINT       NOT NULL DEFAULT 0
);

-- ---------------------------------------------------------------- documentos emitidos
CREATE TABLE issued_document_counter (
    organization_id  UUID        NOT NULL,
    type             VARCHAR(30) NOT NULL,
    year             INT         NOT NULL,
    last_number      INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (organization_id, type, year)
);

CREATE TABLE issued_document (
    id                 UUID PRIMARY KEY,
    organization_id    UUID         NOT NULL REFERENCES organization (id),
    branch_id          UUID         NOT NULL REFERENCES branch (id),
    type               VARCHAR(30)  NOT NULL,
    template_id        UUID         NOT NULL REFERENCES document_template (id),
    template_version   INT          NOT NULL,
    subject_type       VARCHAR(30)  NOT NULL,
    subject_id         UUID         NOT NULL,
    number             INT          NOT NULL,
    document_no        VARCHAR(30)  NOT NULL,
    verification_code  VARCHAR(16)  NOT NULL,
    signatory_id       UUID         REFERENCES signatory (id),
    status             VARCHAR(10)  NOT NULL DEFAULT 'ISSUED',
    void_reason        VARCHAR(300),
    voided_at          TIMESTAMPTZ,
    voided_by          UUID,
    pdf_ref            VARCHAR(255) NOT NULL,
    sha256             VARCHAR(64)  NOT NULL,
    is_duplicate       BOOLEAN      NOT NULL DEFAULT FALSE,
    snapshot           JSONB        NOT NULL,                                                                    -- variables usadas al emitir, para reimprimir con los mismos datos
    issued_at          TIMESTAMPTZ  NOT NULL,
    issued_by          UUID,
    CONSTRAINT ck_issued_status CHECK (status IN ('ISSUED', 'VOIDED')),
    CONSTRAINT ux_issued_number UNIQUE (organization_id, type, number)                                           -- [V13]
);
CREATE UNIQUE INDEX ux_issued_code ON issued_document (verification_code);
-- [V11] un documento vigente por (tipo, sujeto) salvo reimpresión (la reimpresión no crea otra fila: ver IssuedDocumentService)
CREATE UNIQUE INDEX ux_issued_valid_subject ON issued_document (organization_id, type, subject_id) WHERE status = 'ISSUED';
CREATE INDEX ix_issued_org_type ON issued_document (organization_id, type, issued_at DESC);
