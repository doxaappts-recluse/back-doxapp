package pe.dcs.app.features.space_reservation;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import pe.dcs.app.entity.Module;
import pe.dcs.app.entity.ReservableSpace;
import pe.dcs.app.entity.SpaceReservation;
import pe.dcs.app.repository.ModuleRepository;
import pe.dcs.app.repository.UserAccessModulePermissionRepository;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.StatusType;

import java.util.List;
import java.util.UUID;

/**
 * Guard de Reservas de Espacios. SYSTEM queda completamente fuera —
 * mismo criterio que BibleAcademyAccessGuard/DocumentTemplateAccessGuard
 * (nuevos módulos operativos no dan bypass a SYSTEM, ver memoria del
 * usuario). Dos niveles de autoridad:
 *
 * - Catálogo de espacios (ReservableSpace): org admin o branch admin
 *   de su propia sede — NO delegable a un org user (pedido explícito
 *   del usuario: "Branch admin de su sede").
 * - Reservas (SpaceReservation): delegable a org user con permiso
 *   CREATE/EDIT del módulo, siempre acotado a la sede del espacio —
 *   mismo mecanismo que BibleAcademyAccessGuard.canManageClass.
 */
@Component
@RequiredArgsConstructor
public class SpaceReservationAccessGuard {

    private static final String MODULE_CODE = "SPACE_RESERVATION";

    private final AuthContext authContext;
    private final ModuleRepository moduleRepository;
    private final UserAccessModulePermissionRepository userAccessModulePermissionRepository;

    // =========================================================
    // USO GENERAL DEL MÓDULO (listar)
    // =========================================================

    public void assertCanUse() {

        if (isAdmin()) {
            return;
        }

        if (!permissions().isEmpty()) {
            return;
        }

        throw forbidden("action.accederReservasEspacios");
    }

    // =========================================================
    // CATÁLOGO DE ESPACIOS — ORG ADMIN / BRANCH ADMIN, NO DELEGABLE
    // =========================================================

    public boolean canManageSpace(ReservableSpace space) {

        if (authContext.isCurrentOrganizationAdmin()) {
            return true;
        }

        UUID currentBranchId = authContext.getCurrentBranchId();

        boolean ownBranchSpace =
                space.getBranch() != null
                        && currentBranchId != null
                        && space.getBranch().getId().equals(currentBranchId);

        return ownBranchSpace && authContext.isCurrentBranchAdmin();
    }

    public void assertCanManageSpace(ReservableSpace space) {
        if (!canManageSpace(space)) {
            throw forbidden("action.gestionarEsteEspacio");
        }
    }

    public void assertCanCreateSpace() {
        if (!isAdmin()) {
            throw forbidden("action.crearEspaciosReservables");
        }
    }

    // =========================================================
    // RESERVAS — DELEGABLE A LA SEDE
    // =========================================================

    public void assertCanCreateReservation() {

        if (isAdmin()) {
            return;
        }

        if (hasPermission("CREATE")) {
            return;
        }

        throw forbidden("action.crearReservas");
    }

    public boolean canManageReservation(SpaceReservation reservation) {

        if (authContext.isCurrentOrganizationAdmin()) {
            return true;
        }

        UUID currentBranchId = authContext.getCurrentBranchId();

        boolean ownBranchReservation =
                reservation.getSpace() != null
                        && reservation.getSpace().getBranch() != null
                        && currentBranchId != null
                        && reservation.getSpace().getBranch().getId().equals(currentBranchId);

        if (!ownBranchReservation) {
            return false;
        }

        return authContext.isCurrentBranchAdmin() || hasPermission("EDIT");
    }

    public void assertCanManageReservation(SpaceReservation reservation) {
        if (!canManageReservation(reservation)) {
            throw forbidden("action.gestionarEstaReserva");
        }
    }

    // =========================================================
    // SEDE
    // =========================================================

    /** Igual patrón que el resto de features: org admin elige libremente, cualquier otro rol queda ligado a su sede actual. */
    public UUID resolveBranchId(UUID requestedBranchId) {

        if (authContext.isCurrentOrganizationAdmin()) {
            return requestedBranchId;
        }

        return authContext.getCurrentBranchId();
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private boolean isAdmin() {
        return authContext.isCurrentOrganizationAdmin()
                || authContext.isCurrentBranchAdmin();
    }

    private boolean hasPermission(String code) {
        return permissions().contains(code);
    }

    private List<String> permissions() {

        UUID userId = authContext.getUserId();
        UUID organizationId = authContext.getCurrentOrganizationId();
        UUID branchId = authContext.getCurrentBranchId();

        if (userId == null || organizationId == null || branchId == null) {
            return List.of();
        }

        Module module =
                moduleRepository.findByCodeAndStatus(
                        MODULE_CODE,
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

    private Exceptions forbidden(String actionKey) {

        org.springframework.context.MessageSource messageSource = pe.dcs.app.util.MessageSourceHolder.get();

        String action = messageSource != null
                ? messageSource.getMessage(actionKey, null, actionKey, org.springframework.context.i18n.LocaleContextHolder.getLocale())
                : actionKey;

        return new Exceptions("error.noTienePermisosPara", HttpStatus.FORBIDDEN, action);
    }
}
