package pe.dcs.app.features.finance;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import pe.dcs.app.entity.Branch;
import pe.dcs.app.entity.FinancialMovement;
import pe.dcs.app.entity.Module;
import pe.dcs.app.repository.ModuleRepository;
import pe.dcs.app.repository.UserAccessModulePermissionRepository;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.StatusType;

import java.util.List;
import java.util.UUID;

/**
 * Guard de "Finanzas Institucionales": a diferencia de
 * {@link pe.dcs.app.features.event.impl.EventAccessGuard}, acá no
 * existe el concepto de "sede coordinadora"/scope ORGANIZATION —
 * cada movimiento pertenece a exactamente una sede, y la autoridad
 * de aprobación es siempre esa sede (o toda la organización para
 * el org admin). Un org user delegado (permiso CREATE) puede
 * registrar movimientos de SU sede actual, pero nunca aprobarlos.
 */
@Component
@RequiredArgsConstructor
public class FinancialAccessGuard {

    private static final String MODULE_CODE = "FINANCIAL_MOVEMENT";

    private final AuthContext authContext;
    private final ModuleRepository moduleRepository;
    private final UserAccessModulePermissionRepository userAccessModulePermissionRepository;

    /**
     * ¿Puede crear un movimiento en esta sede? Org admin de la
     * organización de la sede, branch admin de esa sede puntual, o
     * un org user delegado con permiso CREATE (siempre y cuando la
     * sede sea la de su acceso actual).
     */
    public void assertCanCreate(Branch branch) {

        if (canApprove(branch)) {
            return;
        }

        if (isDelegatedToCurrentBranch(branch)
                && hasPermission("CREATE")) {
            return;
        }

        throw forbidden("action.registrarMovimientosFinancierosSede");
    }

    /**
     * ¿Puede aprobar/rechazar movimientos de esta sede? Únicamente
     * org admin de la organización o branch admin de esa sede
     * específica — un org user delegado NUNCA aprueba, ni siquiera
     * el movimiento que él mismo creó.
     */
    public boolean canApprove(Branch branch) {

        UUID organizationId = branch.getOrganization().getId();
        UUID branchId = branch.getId();

        return authContext.isSystem()
                || authContext.canManageOrgOrBranchOnly(organizationId, branchId);
    }

    /**
     * ¿Puede editar este movimiento? canApprove(sede) siempre;
     * además, quien lo creó puede editarlo mientras siga PENDING
     * (igual que EventFinance) — pero NUNCA si ya está APPROVED/
     * REJECTED.
     */
    public boolean canManage(FinancialMovement movement) {

        if (canApprove(movement.getBranch())) {
            return true;
        }

        UUID currentUserId = authContext.getUserId();

        return movement.getCreatedByUser() != null
                && currentUserId != null
                && movement.getCreatedByUser().getId().equals(currentUserId);
    }

    public void assertCanManage(FinancialMovement movement) {

        if (!canManage(movement)) {
            throw forbidden("action.gestionarMovimientoFinanciero");
        }
    }

    public void assertSameOrganization(Branch branch) {

        UUID organizationId = branch.getOrganization().getId();

        if (!authContext.isSystem()
                && !organizationId.equals(authContext.getCurrentOrganizationId())) {

            throw new Exceptions(
                    "error.noTieneAccesoSede",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private boolean isDelegatedToCurrentBranch(Branch branch) {

        UUID currentBranchId = authContext.getCurrentBranchId();

        return currentBranchId != null
                && currentBranchId.equals(branch.getId());
    }

    private boolean hasPermission(String code) {
        return permissions().contains(code);
    }

    /**
     * Permisos delegados a la persona actual sobre el módulo
     * FINANCIAL_MOVEMENT, acotados al acceso (organización + sede)
     * puntual activo — mismo mecanismo que EventAccessGuard.
     */
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
