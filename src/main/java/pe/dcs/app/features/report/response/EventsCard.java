package pe.dcs.app.features.report.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class EventsCard {

    /** Eventos con startDate >= ahora, sin importar estado. */
    private long upcomingEvents;
}
