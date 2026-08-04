package pe.dcs.app.features.space_reservation.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.space_reservation.ReservationSourceType;
import pe.dcs.app.util.enums.space_reservation.ReservationStatus;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
public class SpaceReservationFilterRequest {

    private UUID spaceId;

    private String purpose;

    private ReservationSourceType sourceType;

    private ReservationStatus status;

    /** Rango opcional para ver reservas de un día/periodo puntual. */
    private LocalDateTime fromDateTime;
    private LocalDateTime toDateTime;

    /**
     * Solo relevante para org admin (acotar a una sede puntual
     * dentro de su organización, ver Reportes Avanzados); branch
     * admin/org user delegado ya queda fijado a su sede actual por
     * SpaceReservationSpecification.
     */
    private UUID branchId;
}
