package pe.dcs.app.features.finance;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import pe.dcs.app.entity.Branch;
import pe.dcs.app.entity.FinancialCashRegister;
import pe.dcs.app.entity.Module;
import pe.dcs.app.repository.ModuleRepository;
import pe.dcs.app.repository.UserAccessModulePermissionRepository;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.StatusType;

import java.util.List;
import java.util.UUID;

/**
 * Guard de "Caja Diaria" — mismo patrón que
 * {@link FinancialAccessGuard} (módulo propio, delegable a org user
 * acotado a su sede actual), pero a diferencia de Movimientos acá
 * NO hay un paso de aprobación separado: abrir y cerrar son ambas
 * tareas operativas de quien maneja la caja física, así que
 * comparten la misma condición de acceso.
 */
@Component
@RequiredArgsConstructor
public class FinancialCashRegisterAccessGuard {

    private static final String MODULE_CODE = "FINANCIAL_CASH_REGISTER";

    private final AuthContext authContext;
    private final ModuleRepository moduleRepository;
    private final UserAccessModulePermissionRepository userAccessModulePermissionRepository;

    /**
     * ¿Puede abrir una caja en esta sede? Org admin de la
     * organización, branch admin de esa sede puntual, o un org user
     * delegado con permiso CREATE (siempre y cuando la sede sea la
     * de su acceso actual).
     */
    public void assertCanOpen(Branch branch) {

        if (canManageBranch(branch)) {
            return;
        }

        if (isDelegatedToCurrentBranch(branch) && hasPermission("CREATE")) {
            return;
        }

        throw forbidden("action.abrirCajaSede");
    }

    /**
     * ¿Puede cerrar esta caja? Admin de la sede/organización
     * siempre; un org user delegado solo puede cerrar la caja que
     * él mismo abrió (mismo criterio "dueño mientras esté pendiente"
     * que FinancialAccessGuard.canManage() con movimientos PENDING,
     * acá "pendiente" = OPEN).
     */
    public boolean canClose(FinancialCashRegister register) {

        if (canManageBranch(register.getBranch())) {
            return true;
        }

        UUID currentUserId = authContext.getUserId();

        return register.getOpenedByUser() != null
                && currentUserId != null
                && register.getOpenedByUser().getId().equals(currentUserId)
                && hasPermission("CREATE");
    }

    public void assertCanClose(FinancialCashRegister register) {

        if (!canClose(register)) {
            throw forbidden("action.cerrarEstaCaja");
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

    private boolean canManageBranch(Branch branch) {

        return authContext.isSystem()
                || authContext.canManageOrgOrBranchOnly(
                        branch.getOrganization().getId(),
                        branch.getId()
                );
    }

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
     * FINANCIAL_CASH_REGISTER, acotados al acceso (organización +
     * sede) puntual activo — mismo mecanismo que
     * FinancialAccessGuard/EventAccessGuard.
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
