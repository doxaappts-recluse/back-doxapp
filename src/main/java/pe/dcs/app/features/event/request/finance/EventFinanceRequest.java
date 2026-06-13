package pe.dcs.app.features.event.request.finance;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.events.EventFinanceType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class EventFinanceRequest {

    private UUID eventId;

    private EventFinanceType type;

    private String description;

    private BigDecimal amount;

    private LocalDate transactionDate;

    private String observations;
}