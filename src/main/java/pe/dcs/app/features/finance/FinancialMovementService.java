package pe.dcs.app.features.finance;

import pe.dcs.app.features.finance.request.FinancialMovementApproveRequest;
import pe.dcs.app.features.finance.request.FinancialMovementFilter;
import pe.dcs.app.features.finance.request.FinancialMovementRejectRequest;
import pe.dcs.app.features.finance.request.FinancialMovementRequest;
import pe.dcs.app.features.finance.request.FinancialMovementSearchRequest;
import pe.dcs.app.features.finance.response.FinancialDonorResponse;
import pe.dcs.app.features.finance.response.FinancialMovementResponse;
import pe.dcs.app.features.finance.response.FinancialMovementSummaryResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.List;
import java.util.UUID;

public interface FinancialMovementService {

    FinancialMovementResponse create(FinancialMovementRequest request);

    FinancialMovementResponse update(UUID id, FinancialMovementRequest request);

    FinancialMovementResponse approve(UUID id, FinancialMovementApproveRequest request);

    FinancialMovementResponse reject(UUID id, FinancialMovementRejectRequest request);

    FinancialMovementResponse getById(UUID id);

    PageResponse<FinancialMovementResponse> search(FinancialMovementSearchRequest request);

    /**
     * Totales agregados (ingresos/egresos/saldo) para un filtro
     * dado, restringido siempre a movimientos APROBADOS sin
     * importar lo que traiga filters.status — pensado para el
     * saldo de un fondo (filters.fundId) o el total aportado por un
     * donante (filters.personId). Reutiliza el mismo scoping de
     * FinancialMovementSpecification (org/sede) que search().
     */
    FinancialMovementSummaryResponse summary(FinancialMovementFilter filters);

    /**
     * Listado de donantes: agrupa todos los movimientos INCOME/
     * APROBADOS por persona (o en un bucket "anónimo" para los
     * registrados sin personId), con el total aportado y si es
     * miembro activo. Mismo scoping (org/sede) que search()/
     * summary(). No pensado para catálogos masivos: se trae
     * completo, sin paginar (igual criterio que Fondos/Módulos).
     */
    List<FinancialDonorResponse> donors(FinancialMovementFilter filters);
}
