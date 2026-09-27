-- V37 · M22 acceso asistido (AssistedAccessGrant, D3 del spec): único mecanismo por el que personal de plataforma
-- entra temporalmente a los módulos de CONFIGURACIÓN de una organización (nunca datos operativos). Nace de un caso
-- de soporte abierto (support_case), el ORG_ADMIN de la organización aprueba/rechaza/revoca, y expira solo
-- (expires_at, máximo 4 h desde starts_at). Todo lo hecho durante el grant queda en audit_event.assisted_grant_id
-- (columna que ya existía desde V3/V10, sin usar hasta ahora).
--
-- Alcance permitido — decisión de esta entrega, más conservadora que el spec: el spec (M22_soporte_auditoria.md)
-- lista "M02, M04 presentación, M05, M23 y plantillas M18" como módulos de configuración elegibles. Se deja FUERA
-- a propósito M05 (equipo/accesos de la organización: BRANCH_ADMINS, SUPPORT_TEAM, PERMISSION_PROFILES) en esta
-- primera entrega: darle a personal de plataforma la acción de crear/editar accesos de la organización abriría la
-- puerta a que se provisione a sí mismo un acceso persistente de administrador dentro de esa organización, algo
-- que ningún otro mecanismo de este sistema permite y que merece su propia revisión de seguridad antes de
-- habilitarse. La lista blanca real (AssistedAccessService.ALLOWED_SCOPE_MODULES) queda en: ORGANIZATION,
-- ORG_BRANDING, BRANCH, CATALOGS, ORG_SETTINGS, INTEGRATIONS, DOC_TEMPLATES.
--
-- SUPPORT gana la acción 'A' (aprobar) que el spec ya declaraba ("Extras: A aprobar acceso asistido") pero la
-- migración original (V10) no incluyó porque el acceso asistido "quedaba para cuando exista el motor de
-- solicitudes" (ya existe, M21). Como ORG_ADMIN recibe siempre todas las acciones declaradas del módulo, agregar
-- 'A' a SUPPORT le da a cualquier ORG_ADMIN el botón de aprobar/rechazar/revocar sin tocar nada más — excepto que
-- también se lo daría a ORG_BRANCH_ADMIN (que hoy recibe SUPPORT completo por no estar en el mapa de topes de
-- AuthorizationService.BRANCH_ADMIN_CAPS), violando [V9] del spec ("solo ORG_ADMIN aprueba"): se corrige esto en
-- el mismo cambio de AuthorizationService que agrega el reconocimiento del grant, no en esta migración.
update module set actions = array_append(actions, 'A') where code = 'SUPPORT' and not ('A' = any(actions));

create table assisted_access_grant (
    id                uuid primary key,
    organization_id   uuid         not null references organization (id),
    case_id           uuid         not null references support_case (id),
    staff_id          uuid         not null references platform_staff (id),
    scope             text[]       not null,
    reason            varchar(500) not null,
    requested_at      timestamptz  not null,
    approved_by       uuid references person (id),
    denied_by         uuid references person (id),
    denied_reason     varchar(500),
    starts_at         timestamptz,
    expires_at        timestamptz,
    revoked_at        timestamptz,
    revoked_by        uuid,
    status            varchar(10)  not null default 'PENDING',
    created_at        timestamptz  not null,
    created_by        uuid,
    updated_at        timestamptz,
    updated_by        uuid,
    version           bigint       not null default 0,
    constraint ck_aag_status check (status in ('PENDING', 'ACTIVE', 'EXPIRED', 'REVOKED', 'DENIED')),
    constraint ck_aag_scope_notempty check (array_length(scope, 1) > 0)
);
-- [spec] "1 ACTIVE por (staff, org)".
create unique index ux_aag_active on assisted_access_grant (staff_id, organization_id) where status = 'ACTIVE';
create index ix_aag_org_status on assisted_access_grant (organization_id, status);
create index ix_aag_staff_status on assisted_access_grant (staff_id, status);
create index ix_aag_case on assisted_access_grant (case_id);
