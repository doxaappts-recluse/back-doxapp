package pe.dcs.app.features.space_reservation.response;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.auditable.AuditableResponse;
import pe.dcs.app.util.enums.space_reservation.ReservationSourceType;
import pe.dcs.app.util.enums.space_reservation.ReservationStatus;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
public class SpaceReservationResponse extends AuditableResponse {

    private UUID id;

    private UUID spaceId;
    private String spaceName;

    private UUID branchId;
    private String branchName;

    private ReservationSourceType sourceType;
    private UUID sourceId;
    private String purpose;

    private UUID requesterPersonId;
    private String requesterName;

    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;

    private ReservationStatus status;

    private String notes;

    private boolean canManage;
}
