package pe.dcs.app.features.event.request.event;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.events.EventStatus;

import java.time.LocalDateTime;

@Getter
@Setter
public class EventFilter {

    private String name;

    private EventStatus status;

    private LocalDateTime startDateFrom;

    private LocalDateTime startDateTo;
}