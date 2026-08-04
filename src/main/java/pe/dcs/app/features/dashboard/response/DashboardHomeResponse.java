package pe.dcs.app.features.dashboard.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.features.finance.response.FinancialCashRegisterResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Dashboard de bienvenida — cubre los 4 roles (ver
 * DashboardAccessGuard/DashboardServiceImpl.getHome()). A diferencia
 * de {@link pe.dcs.app.features.report.response.ExecutiveDashboardResponse}
 * (grilla completa de tarjetas por módulo, "Reportes Avanzados",
 * exclusivo de admins), esta respuesta es intencionalmente liviana:
 * solo lo accionable del día a día. Los campos de organización/sede
 * (activeMembers..openCashRegister) son null si el módulo que los
 * alimenta no está visible para el rol actual (contratado para
 * admins, delegado para org user); los campos de plataforma
 * (organizationsCount..systemUsersCount) solo se llenan para SYSTEM.
 */
@Getter
@Setter
@Builder
public class DashboardHomeResponse {

    /**
     * "SYSTEM" (resumen de plataforma), "ORGANIZATION" (org admin),
     * "BRANCH" (branch admin) o "USER" (org user delegado).
     */
    private String scope;

    private UUID branchId;
    private String branchName;

    private LocalDateTime generatedAt;

    /** Miembros activos — null si MEMBERSHIP no está contratado. */
    private Long activeMembers;

    /** Próximo evento publicado (org-wide o de la sede) — null si no hay o EVENTS no está contratado. */
    private NextEventResponse nextEvent;

    /** Solicitudes de vacaciones/permisos pendientes — null si LEAVE_REQUEST no está contratado. */
    private Long pendingLeaveRequests;

    /** Movimientos financieros pendientes de aprobación — null si FINANCIAL_MOVEMENT no está contratado. */
    private Long pendingFinancialMovements;

    /**
     * Caja abierta de la sede actual (reutiliza el DTO existente de
     * Finanzas tal cual) — null si no hay caja abierta o
     * FINANCIAL_CASH_REGISTER no está contratado. Para org admin
     * (que no tiene una única sede "actual" fija) este campo queda
     * en null — ver DashboardServiceImpl.
     */
    private FinancialCashRegisterResponse openCashRegister;

    /**
     * Códigos de módulo activos en el contrato actual (org o sede) —
     * ver ContractService.getActiveModuleCodesForCurrentContext().
     * El front lo usa únicamente para decidir qué accesos directos
     * mostrar (no para gatear datos, eso ya lo hace cada campo de
     * arriba viniendo null u omitido).
     */
    private List<String> activeModules;

    // =========================================================
    // SOLO SCOPE "SYSTEM"
    // =========================================================

    /** Total de organizaciones registradas — solo para SYSTEM. */
    private Long organizationsCount;

    /** Contratos ACTIVE vigentes hoy (org + sede, toda la plataforma) — solo para SYSTEM. */
    private Long activeContractsCount;

    /** Contratos ACTIVE que vencen dentro de los próximos 30 días — solo para SYSTEM. */
    private Long contractsExpiringSoon;

    /** Usuarios del sistema (SYSTEM_ADMIN/SYSTEM_SUPPORT) — solo para SYSTEM. */
    private Long systemUsersCount;
}
