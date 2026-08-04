package pe.dcs.app.features.space_reservation.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.StatusType;

import java.util.UUID;

@Getter
@Setter
public class ReservableSpaceFilterRequest {

    private String name;

    /** Solo relevante para org admin; el resto ya queda acotado a su sede por ReservableSpaceSpecification. */
    private UUID branchId;

    private StatusType status;
}
