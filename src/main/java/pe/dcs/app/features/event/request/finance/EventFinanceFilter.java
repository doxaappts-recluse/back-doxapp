package pe.dcs.app.features.event.request.finance;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.events.EventFinanceStatus;
import pe.dcs.app.util.enums.events.EventFinanceType;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class EventFinanceFilter {

    private UUID eventId;

    private EventFinanceType type;

    private EventFinanceStatus status;

    private LocalDate startDate;

    private LocalDate endDate;
}