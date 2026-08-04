package pe.dcs.app.features.finance.request;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class FinancialCashRegisterCloseRequest {

    private BigDecimal closingBalance;

    /** Opcional: típicamente el motivo de una diferencia. */
    private String notes;
}
