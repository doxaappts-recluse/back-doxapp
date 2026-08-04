package pe.dcs.app.features.hr;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import pe.dcs.app.entity.Branch;
import pe.dcs.app.entity.LeaveRequest;
import pe.dcs.app.entity.PayrollRecord;
import pe.dcs.app.entity.StaffMember;
import pe.dcs.app.entity.Module;
import pe.dcs.app.repository.ModuleRepository;
import pe.dcs.app.repository.UserAccessModulePermissionRepository;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.StatusType;

import java.util.List;
import java.util.UUID;

/**
 * Guard de RRHH. SYSTEM queda completamente fuera — mismo criterio
 * que InventoryAccessGuard/SpaceReservationAccessGuard (nuevos
 * módulos operativos no dan bypass a SYSTEM).
 *
 * RRHH se reestructuró de un único módulo "HR" a un padre "Recursos
 * Humanos" con 3 hijos independientes (ver import.sql), cada uno con
 * su propio permiso delegable — por eso {@link #permissions(String)}
 * y {@link #hasPermission(String, String)} reciben el module code en
 * vez de usar una única constante fija:
 *
 * - {@link #STAFF_MEMBER_MODULE_CODE} (Ficha de empleado): org admin
 *   o branch admin de su propia sede gestionan libremente — crear/
 *   editar NO es delegable (mismo criterio que InventoryItem); ver el
 *   listado sí es delegable con permiso VIEW.
 * - {@link #LEAVE_REQUEST_MODULE_CODE} (Vacaciones/Permisos) y
 *   {@link #PAYROLL_MODULE_CODE} (Planilla): delegables a org user
 *   con permiso CREATE/EDIT de su propio módulo, siempre acotado a la
 *   sede del empleado — mismo mecanismo que
 *   InventoryAccessGuard.canManageForBranch (incluye aprobar/
 *   rechazar permisos, que en RRHH también es delegable).
 */
@Component
@RequiredArgsConstructor
public class HrAccessGuard {

    public static final String STAFF_MEMBER_MODULE_CODE = "STAFF_MEMBER";
    public static final String LEAVE_REQUEST_MODULE_CODE = "LEAVE_REQUEST";
    public static final String PAYROLL_MODULE_CODE = "PAYROLL";

    private final AuthContext authContext;
    private final ModuleRepository moduleRepository;
    private final UserAccessModulePermissionRepository userAccessModulePermissionRepository;

    // =========================================================
    // USO GENERAL DEL MÓDULO (listar) — uno por cada hijo, ya que
    // cada uno tiene su propio permiso independiente.
    // =========================================================

    public void assertCanUseStaff() {
        assertCanUse(STAFF_MEMBER_MODULE_CODE, "acceder a fichas de empleado");
    }

    public void assertCanUseLeaveRequest() {
        assertCanUse(LEAVE_REQUEST_MODULE_CODE, "acceder a vacaciones/permisos");
    }

    public void assertCanUsePayroll() {
        assertCanUse(PAYROLL_MODULE_CODE, "acceder a planilla");
    }

    private void assertCanUse(String moduleCode, String action) {

        if (isAdmin()) {
            return;
        }

        if (!permissions(moduleCode).isEmpty()) {
            return;
        }

        throw forbidden(action);
    }

    // =========================================================
    // FICHA DE EMPLEADO — ORG ADMIN / BRANCH ADMIN, NO DELEGABLE
    // =========================================================

    public boolean canManageStaff(StaffMember staff) {

        if (authContext.isCurrentOrganizationAdmin()) {
            return true;
        }

        UUID currentBranchId = authContext.getCurrentBranchId();

        boolean ownBranchStaff =
                staff.getBranch() != null
                        && currentBranchId != null
                        && staff.getBranch().getId().equals(currentBranchId);

        return ownBranchStaff && authContext.isCurrentBranchAdmin();
    }

    public void assertCanManageStaff(StaffMember staff) {
        if (!canManageStaff(staff)) {
            throw forbidden("gestionar esta ficha de empleado");
        }
    }

    public void assertCanCreateStaff() {
        if (!isAdmin()) {
            throw forbidden("crear fichas de empleado");
        }
    }

    // =========================================================
    // VACACIONES/PERMISOS — DELEGABLE A LA SEDE
    // =========================================================

    public void assertCanCreateLeaveRequest() {

        if (isAdmin()) {
            return;
        }

        if (hasPermission("CREATE", LEAVE_REQUEST_MODULE_CODE)) {
            return;
        }

        throw forbidden("registrar solicitudes de vacaciones/permisos");
    }

    public boolean canManageLeaveRequest(LeaveRequest leaveRequest) {
        return canManageForBranch(
                leaveRequest.getStaff() != null ? leaveRequest.getStaff().getBranch() : null,
                LEAVE_REQUEST_MODULE_CODE
        );
    }

    public void assertCanManageLeaveRequest(LeaveRequest leaveRequest) {
        if (!canManageLeaveRequest(leaveRequest)) {
            throw forbidden("gestionar esta solicitud");
        }
    }

    // =========================================================
    // PLANILLA — DELEGABLE A LA SEDE
    // =========================================================

    public void assertCanCreatePayroll() {

        if (isAdmin()) {
            return;
        }

        if (hasPermission("CREATE", PAYROLL_MODULE_CODE)) {
            return;
        }

        throw forbidden("registrar pagos de planilla");
    }

    public boolean canManagePayroll(PayrollRecord payroll) {
        return canManageForBranch(
                payroll.getStaff() != null ? payroll.getStaff().getBranch() : null,
                PAYROLL_MODULE_CODE
        );
    }

    public void assertCanManagePayroll(PayrollRecord payroll) {
        if (!canManagePayroll(payroll)) {
            throw forbidden("gestionar este pago de planilla");
        }
    }

    private boolean canManageForBranch(Branch branch, String moduleCode) {

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

        return authContext.isCurrentBranchAdmin() || hasPermission("EDIT", moduleCode);
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

    private boolean hasPermission(String code, String moduleCode) {
        return permissions(moduleCode).contains(code);
    }

    private List<String> permissions(String moduleCode) {

        UUID userId = authContext.getUserId();
        UUID organizationId = authContext.getCurrentOrganizationId();
        UUID branchId = authContext.getCurrentBranchId();

        if (userId == null || organizationId == null || branchId == null) {
            return List.of();
        }

        Module module =
                moduleRepository.findByCodeAndStatus(
                        moduleCode,
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
