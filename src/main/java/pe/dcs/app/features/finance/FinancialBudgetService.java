package pe.dcs.app.features.finance;

import pe.dcs.app.features.finance.request.FinancialBudgetRequest;
import pe.dcs.app.features.finance.response.FinancialBudgetProgressResponse;
import pe.dcs.app.features.finance.response.FinancialBudgetResponse;

import java.util.List;
import java.util.UUID;

public interface FinancialBudgetService {

    FinancialBudgetResponse create(FinancialBudgetRequest request);

    FinancialBudgetResponse update(UUID id, FinancialBudgetRequest request);

    FinancialBudgetResponse enable(UUID id);

    FinancialBudgetResponse disable(UUID id);

    /**
     * Detalle de un presupuesto puntual, para precargar el
     * formulario de edición (misma regla de acceso que update/
     * enable/disable).
     */
    FinancialBudgetResponse getById(UUID id);

    /**
     * Todos los presupuestos (activos e inactivos) de la
     * organización actual — para la pantalla de administración del
     * catálogo. Mismo criterio que FinancialFundService.listAll().
     */
    List<FinancialBudgetResponse> listAll();

    /**
     * Avance real de un presupuesto puntual contra los movimientos
     * APROBADOS del mismo scope (sede/fondo/categoría) y período —
     * se calcula on-demand, nunca se persiste.
     */
    FinancialBudgetProgressResponse progress(UUID id);

    /**
     * Avance de todos los presupuestos ACTIVOS de la organización
     * actual para un período puntual — pensado para el dashboard de
     * Finanzas (ver tarea siguiente), pero también consumible desde
     * cualquier otra pantalla que necesite el resumen de un mes.
     */
    List<FinancialBudgetProgressResponse> progressForPeriod(Integer periodYear, Integer periodMonth);
}
