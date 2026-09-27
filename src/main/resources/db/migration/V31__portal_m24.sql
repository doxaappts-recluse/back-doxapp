-- V31 · M24 Portal del miembro: shell N4 que agrega alta (invitación y autorregistro con aprobación), directorio
-- opt-in y privacidad. "Cada sección es un /portal/me/* del módulo dueño" (cierre del spec): esta entrega NO crea
-- tablas de negocio nuevas por sección — reutiliza MEMBER (M05), ApprovalEngine (M21), ConsentService (M06b),
-- NotificationService, PersonLookupService/PersonService.registerBasic (M06) y la propia invitación/token de M05.
--
-- [D1] El spec pide portal home con ~15 secciones, una por módulo dueño (M06 perfil/familia, M08 membresía, M09
-- asistencia, M10 grupos, M11 servicio, M12 pastoral, M13 formación, M14 eventos, M15 donaciones, M16 espacios,
-- M17 trabajo, M18 documentos, M19 avisos, M21 solicitudes, M24 directorio). Registrar las ~15 en una sola entrega
-- significa declarar 'N4' en el level_list de ~15 módulos ya validados y reauditar cada uno de sus `switch
-- (actor.role())` internos antes de confiar en que MEMBER no vea nada indebido — el mismo riesgo que D1 de M20 pero
-- multiplicado por 15. Esta entrega construye el shell completo (alta, sesión, home configurable, directorio,
-- privacidad) y resuelve 3 secciones reales sin tocar ningún módulo ajeno: Mi perfil (lectura directa y acotada al
-- propio token, ver [V13]), Mis solicitudes (lectura directa de `approval_request` por `requested_by`, sin pasar
-- por ApprovalEngine.search que está pensado para roles de staff/organización) y Comunicados, que YA funciona sin
-- código nuevo porque `/me/notifications` (M19) no distingue rol. El resto queda con `homeBlocks` deshabilitado por
-- defecto y anotado como pendiente en el "como_ejecutar" — agregar una sección es, en cada módulo dueño, un
-- controlador `/portal/me/*` que lee acotado al `personId` del token; cero cambios a este módulo.
--
-- [D2] Sin SMTP ni pasarela SMS (decisión del cliente, igual que D2 de M20): el paso "código de 6 dígitos al correo
-- o celular" del autorregistro público [V5] NO se implementa — no hay canal para entregarlo. En su lugar el
-- antiabuso queda en el límite por IP+documento ya usado en M07 (`PublicRateLimiter`, aquí `PortalPublicRateLimiter`)
-- más la propia unicidad de documento [V6] revalidada otra vez al aprobar. Queda anotado como pendiente si el
-- proyecto activa un canal de mensajería en el futuro.
--
-- [D3] Sin URL firmada pública (no existe en FileStorageService, mismo criterio que D3 de M17/M18/M20): "mis datos"
-- se descarga por un endpoint autenticado que valida dueño+vigencia de 24h, no por un enlace público sin sesión.
--
-- [D4] Sin infraestructura de push real (no hay VAPID/servicio push en el proyecto): `push_subscription` guarda el
-- registro del dispositivo para cuando exista ese canal; esta entrega no envía ninguna notificación push real — el
-- aviso in-app (campana) sigue siendo el canal efectivo, igual que todo el proyecto desde que se apagó el correo.
--
-- [D5] Sede de un acceso MEMBER (obligatoria por `ck_ua_branch`, ver V2__identity.sql): en invitación, la que elige
-- el administrador; en autorregistro, la sede escogida en el formulario público. No se toca la restricción.

INSERT INTO module (code, name_es, name_en, levels, kind, actions, delegable, route, icon, sort_order) VALUES
 ('PORTAL', 'Portal del miembro', 'Member portal', ARRAY['N2','N3','N4'], 'BASE', ARRAY['V','C','E'], TRUE, 'portal', 'home', 950),
 ('PORTAL_SIGNUP', 'Autorregistro del portal', 'Portal self-registration', ARRAY['N2','N3'], 'CONTRACTABLE', ARRAY['A'], TRUE, 'portal/signups', 'user-add', 951),
 ('PORTAL_DIRECTORY', 'Directorio de miembros', 'Member directory', ARRAY['N2','N3','N4'], 'CONTRACTABLE', ARRAY['V'], TRUE, 'portal/directory', 'team', 952);

-- Configuración del portal: una fila de organización (branch_id null) más, opcionalmente, una fila de override por
-- sede que solo tiene sentido para home_blocks/welcome_text_* (spec: "override de sede solo en bloques+bienvenida").
CREATE TABLE portal_settings (
    id                   UUID         PRIMARY KEY,
    organization_id      UUID         NOT NULL REFERENCES organization (id),
    branch_id            UUID         REFERENCES branch (id),                                            -- null = fila de organización
    signup_mode          VARCHAR(20)  NOT NULL DEFAULT 'INVITE_ONLY',                                     -- solo relevante en la fila de organización
    directory_enabled    BOOLEAN      NOT NULL DEFAULT FALSE,                                             -- idem
    allow_children_view  BOOLEAN      NOT NULL DEFAULT FALSE,                                             -- idem [V12]
    legal_text_version   VARCHAR(20)  NOT NULL DEFAULT 'v1',                                              -- idem
    home_blocks          JSONB        NOT NULL DEFAULT '[]'::jsonb,                                       -- [{code,order,enabled}], override válido a nivel de sede
    welcome_text_es      VARCHAR(2000),
    welcome_text_en      VARCHAR(2000),
    created_at           TIMESTAMPTZ  NOT NULL,
    created_by           UUID,
    updated_at           TIMESTAMPTZ,
    updated_by           UUID,
    version              BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_portal_signup_mode CHECK (signup_mode IN ('INVITE_ONLY','OPEN_WITH_APPROVAL'))
);
CREATE UNIQUE INDEX ux_portal_settings_org    ON portal_settings (organization_id) WHERE branch_id IS NULL;
CREATE UNIQUE INDEX ux_portal_settings_branch ON portal_settings (organization_id, branch_id) WHERE branch_id IS NOT NULL;

-- Preferencia de visibilidad en el directorio (opt-in, oculto por defecto — [V17]).
CREATE TABLE directory_preference (
    person_id   UUID         PRIMARY KEY REFERENCES person (id),
    organization_id UUID     NOT NULL REFERENCES organization (id),
    visible     BOOLEAN      NOT NULL DEFAULT FALSE,
    fields      JSONB        NOT NULL DEFAULT '{"phone":false,"email":false,"photo":false,"groups":false}'::jsonb,
    updated_at  TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_directory_pref_org ON directory_preference (organization_id, visible);

-- Suscripción push por dispositivo (registro únicamente — ver [D4]).
CREATE TABLE push_subscription (
    id          UUID         PRIMARY KEY,
    person_id   UUID         NOT NULL REFERENCES person (id),
    endpoint    VARCHAR(500) NOT NULL,
    keys        JSONB        NOT NULL,
    user_agent  VARCHAR(300),
    created_at  TIMESTAMPTZ  NOT NULL,
    revoked_at  TIMESTAMPTZ,
    CONSTRAINT ux_push_endpoint UNIQUE (person_id, endpoint)
);

-- Solicitud de exportación/eliminación de datos personales, para el límite de 1/24h [V18] sin depender de SupportService
-- (que exige un rol de organización — `requireOrgRole` — que un MEMBER no tiene; ver PrivacyService).
CREATE TABLE portal_data_request (
    id          UUID         PRIMARY KEY,
    person_id   UUID         NOT NULL REFERENCES person (id),
    type        VARCHAR(10)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    support_case_id UUID     REFERENCES support_case (id),
    CONSTRAINT ck_pdr_type CHECK (type IN ('EXPORT','ERASURE'))
);
CREATE INDEX ix_portal_data_request_person ON portal_data_request (person_id, type, created_at DESC);

-- Fila de organización por defecto para cada organización ya existente (INVITE_ONLY, sin bloques activos: el
-- administrador habilita el portal desde /admin/portal/settings). PortalSettingsService la crea igual, perezosa,
-- para cualquier organización que se dé de alta después de esta migración.
INSERT INTO portal_settings (id, organization_id, branch_id, signup_mode, created_at)
SELECT gen_random_uuid(), id, NULL, 'INVITE_ONLY', now() FROM organization
ON CONFLICT DO NOTHING;
