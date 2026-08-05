package pe.dcs.app.features.finance.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class FinancialCashRegisterOpenRequest {

    @NotNull(message = "{error.debeIndicarSede2}")
    private UUID branchId;

    /** Opcional: si no se define, se usa la fecha actual. */
    private LocalDate registerDate;

    @NotNull(message = "{error.montoObligatorio}")
    private BigDecimal openingBalance;

    private String notes;
}
