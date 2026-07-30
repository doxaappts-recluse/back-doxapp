package pe.dcs.app.features.event.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import pe.dcs.app.entity.Event;
import pe.dcs.app.entity.Module;
import pe.dcs.app.repository.EventRepository;
import pe.dcs.app.repository.ModuleRepository;
import pe.dcs.app.repository.UserAccessModulePermissionRepository;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.StatusType;

import java.util.List;
import java.util.UUID;

/**
 * Guard compartido de TODO lo relacionado a Eventos: quién puede
 * crear, ver/inscribir/aportar finanzas, y gestionar (editar,
 * publicar/cancelar, dashboard, reportes, asistencia, aprobar
 * finanzas) — para los tres roles que hoy tocan el módulo:
 *
 * - Org admin: crea/ve/gestiona todo.
 * - Branch admin: crea/ve/gestiona lo de su propia sede (siempre
 *   la sede coordinadora del evento, sin importar el scope).
 * - Org user delegado (módulo EVENTS asignado vía Usuarios de
 *   Acceso, con permisos CREATE/EDIT del catálogo): puede crear si
 *   tiene CREATE; puede VER/inscribir/aportar finanzas de eventos
 *   ORGANIZATION o de su propia sede (igual que un branch admin);
 *   pero solo GESTIONA (editar/publicar/cancelar/dashboard/
 *   reportes/asistencia/aprobar finanzas) el evento que él mismo
 *   creó, y solo si tiene EDIT — es más angosto que un branch
 *   admin, que gestiona TODO lo de su sede sin importar quién lo
 *   creó puntualmente.
 */
@Component
@RequiredArgsConstructor
public class EventAccessGuard {

    private static final String EVENTS_MODULE_CODE = "EVENTS";

    private final EventRepository eventRepository;
    private final AuthContext authContext;
    private final ModuleRepository moduleRepository;
    private final UserAccessModulePermissionRepository userAccessModulePermissionRepository;

    // =========================================================
    // GATES DE ENTRADA (sin evento puntual todavía)
    // =========================================================

    /**
     * ¿Puede crear un evento? Org admin/branch admin siempre; un
     * org user delegado solo si tiene el permiso CREATE en EVENTS.
     */
    public void assertCanCreate() {

        if (isAdmin()) {
            return;
        }

        if (hasEventsPermission("CREATE")) {
            return;
        }

        throw forbidden("crear eventos");
    }

    /**
     * ¿Puede usar el módulo (listar, ver, inscribir, aportar
     * finanzas)? Org admin/branch admin siempre; un org user
     * delegado si tiene CUALQUIER permiso en EVENTS (CREATE o
     * EDIT) — el detalle de qué evento puntual puede ver lo decide
     * canAccess(event).
     */
    public void assertCanUse() {

        if (isAdmin()) {
            return;
        }

        if (!eventsPermissions().isEmpty()) {
            return;
        }

        throw forbidden("gestionar eventos");
    }

    // =========================================================
    // POR EVENTO PUNTUAL
    // =========================================================

    public Event assertCanManage(UUID eventId) {

        Event event =
                eventRepository.findById(eventId)
                        .orElseThrow(() ->
                                new Exceptions(
                                        "Evento no encontrado",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        assertCanManage(event);

        return event;
    }

    public void assertCanManage(Event event) {

        assertSameOrganization(event);

        if (!canManage(event)) {

            throw new Exceptions(
                    "Solo la sede coordinadora, quien creó el evento, o el administrador de la organización pueden gestionarlo",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    public void assertCanAccess(Event event) {

        assertSameOrganization(event);

        if (!canAccess(event)) {

            throw new Exceptions(
                    "No tiene acceso a este evento",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    private void assertSameOrganization(Event event) {

        if (!event.getOrganization()
                .getId()
                .equals(authContext.getCurrentOrganizationId())) {

            throw new Exceptions(
                    "No tiene acceso a este evento",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    /**
     * Tier amplio: VER el evento, inscribir gente, aportar
     * movimientos financieros. Org admin siempre. Para
     * ORGANIZATION (compartido) o para la sede coordinadora
     * (BRANCH), cualquier branch admin de esa sede o cualquier org
     * user delegado en EVENTS.
     */
    public boolean canAccess(Event event) {

        if (authContext.isCurrentOrganizationAdmin()) {
            return true;
        }

        boolean inScope =
                event.isOrganizationScope()
                        || event.isOwnedByBranch(
                                authContext.getCurrentBranchId()
                        );

        if (!inScope) {
            return false;
        }

        return authContext.isCurrentBranchAdmin()
                || !eventsPermissions().isEmpty();
    }

    /**
     * Tier angosto: GESTIONAR el evento (editar, publicar/
     * cancelar, dashboard, reportes, asistencia, aprobar
     * finanzas). Org admin siempre. Branch admin: todo lo de su
     * sede, sin importar quién lo creó puntualmente. Org user
     * delegado: SOLO el evento que él mismo creó, y solo si tiene
     * el permiso EDIT en EVENTS.
     */
    public boolean canManage(Event event) {

        if (authContext.isCurrentOrganizationAdmin()) {
            return true;
        }

        if (authContext.isCurrentBranchAdmin()) {
            return event.isOwnedByBranch(
                    authContext.getCurrentBranchId()
            );
        }

        return hasEventsPermission("EDIT")
                && event.getCreatedBy() != null
                && authContext.getUserId() != null
                && event.getCreatedBy()
                        .getId()
                        .equals(authContext.getUserId());
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private boolean isAdmin() {
        return authContext.isCurrentOrganizationAdmin()
                || authContext.isCurrentBranchAdmin();
    }

    private boolean hasEventsPermission(String code) {
        return eventsPermissions().contains(code);
    }

    /**
     * Permisos delegados a la persona actual sobre el módulo
     * EVENTS, acotados al acceso (organización + sede) puntual
     * activo — mismo mecanismo que el resto de módulos delegables
     * (ver UserAccessModulePermissionRepository).
     */
    private List<String> eventsPermissions() {

        UUID userId = authContext.getUserId();
        UUID organizationId = authContext.getCurrentOrganizationId();
        UUID branchId = authContext.getCurrentBranchId();

        if (userId == null || organizationId == null || branchId == null) {
            return List.of();
        }

        Module module =
                moduleRepository.findByCodeAndStatus(
                        EVENTS_MODULE_CODE,
                        StatusType.ACTIVE
                ).orElse(null);

        if (module == null) {
            return List.of();
        }

        return userAccessModulePermissionRepository.findPermissionsByAccessContext(
                userId,
                organizationId,
                branchId,
                module.getId(),
                StatusType.ACTIVE
        );
    }

    private Exceptions forbidden(String action) {
        return new Exceptions(
                "No tiene permisos para " + action + ".",
                HttpStatus.FORBIDDEN
        );
    }
}
