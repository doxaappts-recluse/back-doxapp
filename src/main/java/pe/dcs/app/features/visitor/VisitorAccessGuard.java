package pe.dcs.app.features.visitor;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import pe.dcs.app.entity.Branch;
import pe.dcs.app.entity.Module;
import pe.dcs.app.repository.ModuleRepository;
import pe.dcs.app.repository.UserAccessModulePermissionRepository;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.StatusType;

import java.util.List;
import java.util.UUID;

/**
 * Guard de Visitantes. Forma parte del paquete comercial "CRM
 * Pastoral" junto a Seguimiento Pastoral — por eso SYSTEM NO tiene
 * bypass acá (corregido: antes se seedeaba como módulo base/gratuito,
 * mismo criterio que Academia Bíblica en adelante, ver
 * DocumentTemplateAccessGuard). ORG_ADMIN/ORG_BRANCH_ADMIN siempre,
 * delegable a ORG_USER (con permiso CREATE puede registrar visitantes
 * de su propia sede — típicamente quien recibe en la puerta; con EDIT
 * puede editar/convertir a miembro cualquier visitante de su sede, no
 * solo los que él mismo registró).
 */
@Component
@RequiredArgsConstructor
public class VisitorAccessGuard {

    private static final String MODULE_CODE = "VISITOR";

    private final AuthContext authContext;
    private final ModuleRepository moduleRepository;
    private final UserAccessModulePermissionRepository userAccessModulePermissionRepository;

    public void assertCanUse() {

        if (isAdmin()) {
            return;
        }

        if (!permissions().isEmpty()) {
            return;
        }

        throw forbidden("acceder a visitantes");
    }

    public void assertCanCreate() {

        if (isAdmin()) {
            return;
        }

        if (hasPermission("CREATE")) {
            return;
        }

        throw forbidden("registrar visitantes");
    }

    public void assertCanManage(Branch visitorBranch) {

        if (!canManage(visitorBranch)) {
            throw new Exceptions(
                    "No tiene permisos para gestionar este visitante.",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    public boolean canManage(Branch visitorBranch) {

        if (isAdmin()) {
            return true;
        }

        UUID currentBranchId = authContext.getCurrentBranchId();

        boolean ownBranch =
                visitorBranch != null
                        && currentBranchId != null
                        && visitorBranch.getId().equals(currentBranchId);

        return ownBranch && hasPermission("EDIT");
    }

    /**
     * Igual criterio que MarriageServiceImpl.resolveBranch/
     * DocumentTemplateAccessGuard.resolveBranchId: org admin elige
     * libremente, cualquier otro rol usa siempre su sede actual.
     */
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
        return authContext.canManageOrgOrBranchOnly(
                authContext.getCurrentOrganizationId(),
                authContext.getCurrentBranchId()
        );
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

    private Exceptions forbidden(String action) {
        return new Exceptions(
                "No tiene permisos para " + action + ".",
                HttpStatus.FORBIDDEN
        );
    }
}
