package pe.dcs.app.features.finance.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.finance.FinancialMovementCategory;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class FinancialBudgetRequest {

    @NotBlank(message = "{error.elNombreEsObligatorio}")
    private String name;

    private String description;

    /** Opcional: sin definir, el presupuesto es org-wide. */
    private UUID branchId;

    /** Opcional: a qué fondo aplica. */
    private UUID fundId;

    /** Opcional: sin definir, cubre todas las categorías. */
    private FinancialMovementCategory category;

    @NotNull(message = "{error.anioPeriodoObligatorio}")
    private Integer periodYear;

    /** 1-12. */
    @NotNull(message = "{error.mesPeriodoObligatorio}")
    private Integer periodMonth;

    @NotNull(message = "{error.montoObligatorio}")
    private BigDecimal amount;
}
