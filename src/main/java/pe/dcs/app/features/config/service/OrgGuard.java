package pe.dcs.app.features.config.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import pe.dcs.app.features.organization.domain.Organization;
import pe.dcs.app.features.organization.domain.OrganizationRepository;
import pe.dcs.app.features.organization.domain.OrganizationStatus;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.RoleType;

import java.util.UUID;

/** Reglas comunes de M23 para lo que escribe una organización: rol y organización cerrada. */
@Component
@RequiredArgsConstructor
public class OrgGuard {

    private final OrganizationRepository organizations;

    /**
     * La configuración de la organización la escribe solo su ORG_ADMIN (ORG_BRANCH_ADMIN recibe todas las acciones
     * por rol) — o personal de plataforma en modo asistido (M22, D3): para cuando llega aquí, {@code @ModuleAccess}
     * ya validó el módulo y la acción exactos contra su grant activo, así que aceptarlo aquí no abre nada nuevo.
     */
    public void requireOrgAdmin(AuthenticatedActor actor) {
        if (actor.role() != RoleType.ORG_ADMIN && !(actor.isStaff() && actor.isAssisted())) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
    }

    /**
     * Las rutas /admin son de una organización: el personal de plataforma (sin organización) no entra aunque el
     * módulo declare N1 — excepto en modo asistido (M22, D3), donde {@code organizationId} queda fijado a la del
     * grant activo y el alcance real (qué módulos, y solo mientras el grant siga vivo) lo decide
     * {@code AuthorizationService.effectiveActions}, no esta puerta.
     */
    public AuthenticatedActor orgActor(AuthenticatedActor actor) {
        if (actor.isStaff() && !actor.isAssisted()) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
        if (actor.organizationId() == null) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
        return actor;
    }

    /** Las rutas /platform son del personal de plataforma aunque el módulo también declare N2/N3 (p. ej. Catálogos). */
    public AuthenticatedActor staffActor(AuthenticatedActor actor) {
        if (!actor.isStaff()) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
        return actor;
    }

    public Organization assertOpen(UUID orgId) {
        Organization o = organizations.findById(orgId)
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
        if (o.getStatus() == OrganizationStatus.CLOSED) {
            throw new Exceptions("error.config.orgClosed", HttpStatus.CONFLICT);
        }
        return o;
    }
}
