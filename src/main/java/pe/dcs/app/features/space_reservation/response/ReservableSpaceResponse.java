package pe.dcs.app.features.space_reservation.response;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.auditable.AuditableResponse;
import pe.dcs.app.util.enums.StatusType;

import java.util.UUID;

@Getter
@Setter
public class ReservableSpaceResponse extends AuditableResponse {

    private UUID id;

    private String name;
    private String description;
    private Integer capacity;

    private UUID branchId;
    private String branchName;

    private StatusType status;

    private long reservationCount;

    private boolean canManage;
}
