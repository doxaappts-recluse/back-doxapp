package pe.dcs.app.features.finance.request;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class FinancialCashRegisterOpenRequest {

    private UUID branchId;

    /** Opcional: si no se define, se usa la fecha actual. */
    private LocalDate registerDate;

    private BigDecimal openingBalance;

    private String notes;
}
