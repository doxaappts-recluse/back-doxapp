package pe.dcs.app.features.event.request.finance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.events.EventFinanceType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class EventFinanceRequest {

    @NotNull(message = "{error.elEventoEsRequerido}")
    private UUID eventId;

    @NotNull(message = "{error.tipoMovimientoObligatorio}")
    private EventFinanceType type;

    @NotBlank(message = "{error.descripcionMovimientoObligatoria}")
    private String description;

    @NotNull(message = "{error.montoObligatorio}")
    private BigDecimal amount;

    @NotNull(message = "{error.fechaTransaccionObligatoria}")
    private LocalDate transactionDate;

    private String observations;
}