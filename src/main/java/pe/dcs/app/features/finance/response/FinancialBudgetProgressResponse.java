package pe.dcs.app.features.finance.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Avance real de un presupuesto puntual — se calcula on-demand en
 * FinancialBudgetServiceImpl.progress(), nunca se persiste (ver
 * comentario en la entity FinancialBudget). El signo de
 * "actualAmount" depende de la categoría: si es una categoría de
 * ingreso (o sin categoría, mezcla todo), representa lo recaudado;
 * si es EXPENSE, lo gastado.
 */
@Getter
@Setter
@Builder
public class FinancialBudgetProgressResponse {

    private UUID budgetId;
    private String name;

    private BigDecimal budgetedAmount;
    private BigDecimal actualAmount;

    /** (actualAmount / budgetedAmount) * 100, 0 si budgetedAmount es 0. */
    private BigDecimal percentageUsed;

    private boolean overBudget;
}
