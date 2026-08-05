package pe.dcs.app.features.finance.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.finance.FinancialMovementCategory;
import pe.dcs.app.util.enums.finance.FinancialMovementPaymentMethod;
import pe.dcs.app.util.enums.finance.FinancialMovementType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class FinancialMovementRequest {

    private UUID branchId;

    @NotNull(message = "{error.tipoMovimientoObligatorio}")
    private FinancialMovementType type;

    @NotNull(message = "{error.categoriaObligatoria}")
    private FinancialMovementCategory category;

    /**
     * Donante, opcional (diezmo/ofrenda/donación). Null = anónimo.
     */
    private UUID personId;

    /**
     * Fondo al que pertenece el dinero, opcional (ver
     * FinancialFund). Independiente de la categoría.
     */
    private UUID fundId;

    private FinancialMovementPaymentMethod paymentMethod;

    @NotBlank(message = "{error.conceptoObligatorio}")
    private String concept;

    @NotNull(message = "{error.montoObligatorio}")
    private BigDecimal amount;

    @NotNull(message = "{error.fechaTransaccionObligatoria}")
    private LocalDate movementDate;

    private String observations;
}
