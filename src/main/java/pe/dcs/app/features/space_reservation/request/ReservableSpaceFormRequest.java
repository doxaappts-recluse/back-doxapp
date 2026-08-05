package pe.dcs.app.features.space_reservation.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.StatusType;

import java.util.UUID;

@Getter
@Setter
public class ReservableSpaceFormRequest {

    @NotBlank(message = "{error.nombreEspacioObligatorio}")
    private String name;
    private String description;
    private Integer capacity;

    /** Solo relevante para org admin (elige sede libremente); igual criterio que el resto de features. */
    private UUID branchId;

    private StatusType status;
}
