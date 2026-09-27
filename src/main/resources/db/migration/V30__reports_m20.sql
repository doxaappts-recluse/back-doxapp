-- V30 · M20 Reportes: catálogo de reportes estándar (uno por módulo, vía el SPI ReportProvider — "M20 solo orquesta,
-- no escribe consultas propias"), vistas guardadas, programaciones de envío y trabajos de exportación con vencimiento.
--
-- [D1] El spec pide "cada módulo aporta sus reportes" para ~13 áreas (personas, asistencia, visitantes, ritos, grupos,
-- voluntariado, pastoral, formación, eventos, finanzas, recursos, RRHH, plataforma). Registrar los ~13 en una sola
-- entrega significa tocar 13 paquetes ya validados solo para agregarles un bean de reporte. Esta entrega construye el
-- motor completo (SPI, catálogo, ejecución, exportación, vistas guardadas, programación) y registra 5 proveedores
-- reales que cubren cada categoría de la matriz de reglas (N1 agregado de plataforma, N2 con columnas sensibles [V6],
-- N2 sin sensibles, alcance por sede): PERSON_GROWTH (PERSON), ATTENDANCE_TREND (ATTENDANCE), FINANCE_SUMMARY
-- (FIN_MOVEMENTS), HR_PAYROLL_SUMMARY (HR_PAYROLL, con `H` para los montos) y PLATFORM_ADOPTION (N1). Agregar el
-- resto es registrar un bean `ReportProvider` nuevo: cero cambios al núcleo de M20 — es exactamente el punto del SPI.
-- Queda anotado como pendiente en el "como_ejecutar".
--
-- [D2] Sin SMTP en el proyecto (decisión del cliente): la "programación de envíos" (`ScheduledReport`) no manda
-- correo — genera el `ExportJob` y avisa a cada destinatario por la campana in-app (NotificationService), con enlace
-- de descarga, igual que toda notificación del proyecto desde que se apagó el correo.
--
-- [D3] Sin un servicio de URLs firmadas (no existe en `FileStorageService`): la "URL firmada que vence a 24h" del
-- spec es un endpoint autenticado (`/admin/export-jobs/{id}/download`) que valida dueño+organización+`expiresAt`,
-- no una URL pública sin sesión. Mismo criterio de simplificación que D3 de M17/M18 (sin NumberingService/ExportService
-- compartidos: cada módulo, incluido este, construye su propio mecanismo mínimo).
--
-- [D4] Sin cola de trabajos en el proyecto: `ExportJob` no corre en segundo plano — se genera en la misma petición
-- (síncrono) hasta el tope de 50 000 filas [V4]; superado ese tope, se pide acotar filtros en vez de encolar. El
-- registro `ExportJob` igual existe (para el historial y la ventana de descarga de 24h), solo que su `status` pasa
-- directo a READY o FAILED, nunca queda en QUEUED/RUNNING de verdad.
--
-- [D5] Formatos de exportación: se implementan XLSX (motor `XlsxWriter` ya usado por todos los módulos) y CSV
-- (trivial); PDF de un reporte tabular NO se implementa en esta entrega (el motor de PDF de M18 es para plantillas
-- de documento posicionadas, no para tablas de reporte) — si se pide PDF, se devuelve XLSX y se avisa en la respuesta.

INSERT INTO module (code, name_es, name_en, levels, kind, actions, delegable, route, icon, sort_order) VALUES
 ('PLATFORM_REPORTS', 'Reportes de plataforma', 'Platform reports', ARRAY['N1'], 'BASE', ARRAY['V','X'], FALSE, 'reports', 'bar-chart', 900),
 ('REPORTS', 'Reportes', 'Reports', ARRAY['N2','N3'], 'CONTRACTABLE', ARRAY['V','X'], TRUE, 'reports', 'bar-chart', 350);

CREATE TABLE saved_report (
    id               UUID         PRIMARY KEY,
    organization_id  UUID         NOT NULL REFERENCES organization (id),
    person_id        UUID         NOT NULL,                                                                    -- dueño (persona con acceso ORG_*), sin FK dura a user_access
    code             VARCHAR(40)  NOT NULL,
    name             VARCHAR(120) NOT NULL,
    filters          JSONB        NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ux_saved_report UNIQUE (organization_id, person_id, name)                                        -- [V9-saved] unique(user, name)
);

CREATE TABLE scheduled_report (
    id               UUID         PRIMARY KEY,
    organization_id  UUID         NOT NULL REFERENCES organization (id),
    code             VARCHAR(40)  NOT NULL,
    filters          JSONB        NOT NULL,
    frequency        VARCHAR(10)  NOT NULL,
    day_of_week      SMALLINT,                                                                                 -- 1=lunes .. 7=domingo, para WEEKLY
    day_of_month     SMALLINT,                                                                                 -- 1..28, para MONTHLY (evita fin de mes corto)
    format           VARCHAR(10)  NOT NULL DEFAULT 'XLSX',
    recipients       UUID[]       NOT NULL,                                                                    -- person_id[]
    status           VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    last_run_at      TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL,
    created_by       UUID,
    updated_at       TIMESTAMPTZ,
    updated_by       UUID,
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_sched_freq   CHECK (frequency IN ('WEEKLY','MONTHLY')),
    CONSTRAINT ck_sched_format CHECK (format IN ('XLSX','PDF','CSV')),
    CONSTRAINT ck_sched_status CHECK (status IN ('ACTIVE','PAUSED'))
);
CREATE INDEX ix_sched_report_org ON scheduled_report (organization_id, status);

CREATE TABLE export_job (
    id               UUID         PRIMARY KEY,
    organization_id  UUID         REFERENCES organization (id),                                                -- null = exportación de plataforma (N1)
    owner_type       VARCHAR(10)  NOT NULL,                                                                    -- PERSON | STAFF
    owner_id         UUID         NOT NULL,
    code             VARCHAR(40)  NOT NULL,
    filters          JSONB        NOT NULL,
    format           VARCHAR(10)  NOT NULL,
    row_count        INT          NOT NULL DEFAULT 0,
    status           VARCHAR(10)  NOT NULL DEFAULT 'QUEUED',
    file_ref         VARCHAR(255),
    error_message    VARCHAR(300),
    expires_at       TIMESTAMPTZ  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_export_owner  CHECK (owner_type IN ('PERSON','STAFF')),
    CONSTRAINT ck_export_status CHECK (status IN ('QUEUED','RUNNING','READY','FAILED','EXPIRED'))
);
CREATE INDEX ix_export_job_owner ON export_job (owner_type, owner_id, created_at DESC);
