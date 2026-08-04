package pe.dcs.app.features.report.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Filtro de las tablas de detalle de Reportes Avanzados (Finanzas,
 * Eventos, ...). A diferencia de {@link pe.dcs.app.features.report.response.ExecutiveDashboardResponse}
 * (tarjetas de resumen sin filtros), estos endpoints devuelven el
 * detalle fila por fila ya filtrado — pensado para verse en tabla y
 * exportarse a Excel.
 * <p>
 * branchId solo aplica para org admin (acotar a una sede puntual
 * dentro de su organización); si quien llama es branch admin, se
 * ignora lo que venga acá y se fuerza su propia sede — ver
 * AdvancedReportsServiceImpl.resolveEffectiveBranchId().
 */
@Getter
@Setter
public class ReportFilterRequest {

    private LocalDate startDate;

    private LocalDate endDate;

    private UUID branchId;
}
