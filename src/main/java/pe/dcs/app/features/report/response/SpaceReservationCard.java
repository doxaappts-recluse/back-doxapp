package pe.dcs.app.features.report.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class SpaceReservationCard {

    private long activeSpaces;

    /** Reservas con fecha de inicio >= ahora, sin importar estado. */
    private long upcomingReservations;
}
