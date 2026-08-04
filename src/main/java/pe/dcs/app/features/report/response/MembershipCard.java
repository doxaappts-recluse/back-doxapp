package pe.dcs.app.features.report.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Tarjeta del Dashboard Ejecutivo para el módulo MEMBERSHIP. Solo se
 * incluye en {@link ExecutiveDashboardResponse} si ese módulo está
 * activo en el contrato actual (ver AdvancedReportsServiceImpl).
 */
@Getter
@Setter
@Builder
public class MembershipCard {

    /** Membresías con status ACTIVE, en el alcance actual (org o sede). */
    private long activeMembers;
}
