package pe.dcs.app.features.inventory;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import pe.dcs.app.entity.Branch;
import pe.dcs.app.entity.InventoryAssignment;
import pe.dcs.app.entity.InventoryItem;
import pe.dcs.app.entity.InventoryMovement;
import pe.dcs.app.entity.Module;
import pe.dcs.app.repository.ModuleRepository;
import pe.dcs.app.repository.UserAccessModulePermissionRepository;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.StatusType;

import java.util.List;
import java.util.UUID;

/**
 * Guard de Inventario. SYSTEM queda completamente fuera — mismo
 * criterio que SpaceReservationAccessGuard/BibleAcademyAccessGuard
 * (nuevos módulos operativos no dan bypass a SYSTEM). Dos niveles de
 * autoridad:
 *
 * - Catálogo de ítems (InventoryItem): org admin o branch admin de
 *   su propia sede — NO delegable (mismo criterio que
 *   ReservableSpace).
 * - Movimientos (InventoryMovement) y asignaciones/custodia
 *   (InventoryAssignment): delegable a org user con permiso
 *   CREATE/EDIT del módulo, siempre acotado a la sede del ítem —
 *   mismo mecanismo que SpaceReservationAccessGuard.canManageReservation.
 */
@Component
@RequiredArgsConstructor
public class InventoryAccessGuard {

    private static final String MODULE_CODE = "INVENTORY";

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

        throw forbidden("acceder a Inventario");
    }

    // =========================================================
    // CATÁLOGO DE ÍTEMS — ORG ADMIN / BRANCH ADMIN, NO DELEGABLE
    // =========================================================

    public boolean canManageItem(InventoryItem item) {

        if (authContext.isCurrentOrganizationAdmin()) {
            return true;
        }

        UUID currentBranchId = authContext.getCurrentBranchId();

        boolean ownBranchItem =
                item.getBranch() != null
                        && currentBranchId != null
                        && item.getBranch().getId().equals(currentBranchId);

        return ownBranchItem && authContext.isCurrentBranchAdmin();
    }

    public void assertCanManageItem(InventoryItem item) {
        if (!canManageItem(item)) {
            throw forbidden("gestionar este ítem");
        }
    }

    public void assertCanCreateItem() {
        if (!isAdmin()) {
            throw forbidden("crear ítems de inventario");
        }
    }

    // =========================================================
    // MOVIMIENTOS — DELEGABLE A LA SEDE
    // =========================================================

    public void assertCanCreateMovement() {

        if (isAdmin()) {
            return;
        }

        if (hasPermission("CREATE")) {
            return;
        }

        throw forbidden("registrar movimientos de inventario");
    }

    public boolean canManageMovement(InventoryMovement movement) {
        return canManageForBranch(
                movement.getItem() != null ? movement.getItem().getBranch() : null
        );
    }

    public void assertCanManageMovement(InventoryMovement movement) {
        if (!canManageMovement(movement)) {
            throw forbidden("gestionar este movimiento");
        }
    }

    // =========================================================
    // ASIGNACIONES / CUSTODIA — DELEGABLE A LA SEDE
    // =========================================================

    public void assertCanCreateAssignment() {

        if (isAdmin()) {
            return;
        }

        if (hasPermission("CREATE")) {
            return;
        }

        throw forbidden("registrar asignaciones de inventario");
    }

    public boolean canManageAssignment(InventoryAssignment assignment) {
        return canManageForBranch(
                assignment.getItem() != null ? assignment.getItem().getBranch() : null
        );
    }

    public void assertCanManageAssignment(InventoryAssignment assignment) {
        if (!canManageAssignment(assignment)) {
            throw forbidden("gestionar esta asignación");
        }
    }

    private boolean canManageForBranch(Branch branch) {

        if (authContext.isCurrentOrganizationAdmin()) {
            return true;
        }

        UUID currentBranchId = authContext.getCurrentBranchId();

        boolean ownBranch =
                branch != null
                        && currentBranchId != null
                        && branch.getId().equals(currentBranchId);

        if (!ownBranch) {
            return false;
        }

        return authContext.isCurrentBranchAdmin() || hasPermission("EDIT");
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

    private Exceptions forbidden(String action) {
        return new Exceptions(
                "No tiene permisos para " + action + ".",
                HttpStatus.FORBIDDEN
        );
    }
}
