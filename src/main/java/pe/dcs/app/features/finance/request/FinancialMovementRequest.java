package pe.dcs.app.features.finance.request;

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

    private FinancialMovementType type;

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

    private String concept;

    private BigDecimal amount;

    private LocalDate movementDate;

    private String observations;
}
