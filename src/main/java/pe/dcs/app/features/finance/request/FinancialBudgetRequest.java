package pe.dcs.app.features.finance.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.finance.FinancialMovementCategory;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class FinancialBudgetRequest {

    private String name;

    private String description;

    /** Opcional: sin definir, el presupuesto es org-wide. */
    private UUID branchId;

    /** Opcional: a qué fondo aplica. */
    private UUID fundId;

    /** Opcional: sin definir, cubre todas las categorías. */
    private FinancialMovementCategory category;

    private Integer periodYear;

    /** 1-12. */
    private Integer periodMonth;

    private BigDecimal amount;
}
