package pe.dcs.app.util.enums.hr;

/**
 * Estado de aprobación de una {@link pe.dcs.app.entity.LeaveRequest}.
 * Igual criterio que FinancialMovementStatus: quien tiene autoridad
 * sobre la sede (admin/branch admin, o delegado con permiso EDIT —
 * ver HrAccessGuard.canManageForBranch) puede pasarla directo a
 * APPROVED al crearla; un registro delegado sin esa autoridad queda
 * en PENDING (no aplica acá porque, a diferencia de Finanzas, la
 * aprobación de RRHH también es delegable — ver
 * HrServiceImpl.createLeaveRequest).
 */
public enum HrApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED
}
