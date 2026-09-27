package pe.dcs.app.features.portal.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.RoleType;

/**
 * M24 · candado de {@code /portal/me/**} y {@code /portal/directory}: SOLO MEMBER. A diferencia de un módulo normal,
 * no se resuelve con {@code AuthorizationService.require} porque ORG_ADMIN/ORG_BRANCH_ADMIN ya tienen V/C/E sobre
 * PORTAL para SU PROPIA vista de administración (ver {@code AdminPortalController}) y no deben colarse en la vista
 * del miembro por compartir el mismo módulo. {@code error.portal.wrongArea} es el mensaje del spec para este caso.
 */
@Component
@RequiredArgsConstructor
public class PortalGuard {

    private final PortalSettingsService settings;

    public void requireMember(AuthenticatedActor actor) {
        if (actor.isStaff() || actor.role() != RoleType.MEMBER) {
            throw new Exceptions("error.portal.wrongArea", HttpStatus.FORBIDDEN);
        }
    }

    /**
     * M24 N4 (seguimiento 2026-09-26): candado real de {@code portal_settings.home_blocks} para las secciones
     * diferidas del portal. Hasta esta entrega, {@code home_blocks} solo decidía qué tarjeta se muestra en
     * {@code /me/home} ({@link PortalMeService#home}) — cualquier miembro podía llamar directo, por ejemplo,
     * a {@code /me/giving} aunque el bloque {@code GIVING} estuviera apagado en la organización, porque ningún
     * otro método volvía a consultar esa lista. A partir de ahora, cada endpoint de una sección diferida llama
     * este método (que ya incluye {@link #requireMember}) en vez de solo {@code requireMember}, pasándole el
     * código exacto de {@code HomeBlock} de esa sección (el mismo que usa el front en {@code HOME_BLOCK_CODES}):
     * {@code FAMILY, MEMBERSHIP, ATTENDANCE, GROUPS, SERVICE, PASTORAL_CARE, TRAINING, EVENTS, GIVING, SPACES,
     * MY_WORK, DOCUMENTS}. Se resuelve con {@link PortalSettingsService#effectiveForMember} — organización, con el
     * override de sede si existe, igual que ya hace {@code home()} — y se corta con 403
     * {@code error.portal.blockDisabled} si el bloque no aparece en la lista o aparece con {@code enabled=false}.
     * <p>
     * Deliberadamente NO se usan estos códigos ni este candado en {@code /me/home}, {@code /me/profile},
     * {@code /me/requests} (las secciones que M24 ya trajo completas de fábrica, sin depender de un bloque para
     * funcionar), en {@code /me/directory-preference} ni en {@code /directory} (que ya tiene su propio candado de
     * contrato vía {@code authz.require(actor, "PORTAL_DIRECTORY", Action.V)}, un mecanismo distinto y anterior),
     * ni en privacidad/push (no son "secciones" con tarjeta propia en el inicio).
     * <p>
     * Nunca 404: a diferencia del resto del módulo (donde una fila ajena siempre da 404 para no delatar
     * existencia, criterio anti-IDOR), esto no es un problema de propiedad de datos sino de configuración de la
     * organización — 403 es el código correcto, igual que {@code error.common.moduleNotContracted} en el resto
     * del sistema para "esto existe pero tu organización no lo tiene habilitado".
     */
    public void requireBlockEnabled(AuthenticatedActor actor, String blockCode) {
        requireMember(actor);
        var effective = settings.effectiveForMember(actor.organizationId(), actor.activeBranchId());
        boolean enabled = effective.homeBlocks().stream().anyMatch(b -> blockCode.equals(b.code()) && b.enabled());
        if (!enabled) {
            throw new Exceptions("error.portal.blockDisabled", HttpStatus.FORBIDDEN);
        }
    }
}
