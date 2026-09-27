package pe.dcs.app.security;

import pe.dcs.app.features.access.domain.ActorType;
import pe.dcs.app.util.enums.RoleType;

import java.util.Set;
import java.util.UUID;

/**
 * Quién hace la petición, reconstruido desde los claims del JWT (spec 00 §5):
 * sub=credentialId, actorType, ctxId (accessId | staffId), orgId, branchIds, role, contractState.
 * ORG_ADMIN lleva branchIds vacío = todas las sedes de la organización. {@code activeBranchId} (claim wbr) es la sede
 * con la que se está trabajando (la elegida al ingresar); no restringe el alcance del ORG_ADMIN.
 * {@code assistedGrantId} (claim ag, M22 D3): solo lo lleva un token de personal de plataforma que entró en modo
 * asistido a una organización ({@code organizationId} queda fijado a la de ese grant mientras dure). Sin este claim,
 * {@code organizationId} de un actor STAFF siempre es null (personal de plataforma no pertenece a ninguna
 * organización fuera de un grant activo).
 */
public record AuthenticatedActor(
        UUID credentialId,
        ActorType actorType,
        UUID ownerId,
        UUID contextId,
        UUID organizationId,
        Set<UUID> branchIds,
        UUID activeBranchId,
        RoleType role,
        ContractState contractState,
        TokenType tokenType,
        UUID assistedGrantId
) {
    public boolean isStaff() {
        return actorType == ActorType.STAFF;
    }

    public boolean allBranches() {
        return role == RoleType.ORG_ADMIN;
    }

    public boolean isAssisted() {
        return assistedGrantId != null;
    }

    /**
     * M22 (D3, acceso asistido): true para el ORG_ADMIN real de la organización, y también para personal de
     * plataforma en modo asistido — quien llama esto ya pasó el chequeo de módulo+acción de
     * {@code AuthorizationService.effectiveActions} (vía {@code @ModuleAccess}), así que tratarlo aquí como si
     * fuera ORG_ADMIN no abre nada que ese chequeo previo no haya abierto ya. Reemplaza los `actor.role() ==
     * RoleType.ORG_ADMIN` sueltos que existían dentro de los servicios de los módulos del alcance asistido
     * (ORGANIZATION, ORG_SETTINGS...) antes de M22 V37.
     */
    public boolean actsAsOrgAdmin() {
        return role == RoleType.ORG_ADMIN || (isStaff() && isAssisted());
    }
}
