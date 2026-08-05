package pe.dcs.app.features.finance.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class FinancialCashRegisterCloseRequest {

    @NotNull(message = "{error.montoObligatorio}")
    private BigDecimal closingBalance;

    /** Opcional: típicamente el motivo de una diferencia. */
    private String notes;
}
