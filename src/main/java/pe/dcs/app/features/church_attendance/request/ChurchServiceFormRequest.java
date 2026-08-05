package pe.dcs.app.features.church_attendance.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.StatusType;

import java.util.UUID;

@Getter
@Setter
public class ChurchServiceFormRequest {

    @NotBlank(message = "{error.nombreCultoObligatorio}")
    private String name;

    @NotBlank(message = "{error.diaCultoObligatorio}")
    private String dayOfWeek;

    @NotBlank(message = "{error.horaCultoObligatoria}")
    private String timeOfDay;

    private StatusType status;

    /**
     * Solo relevante para org admin (elige sede libremente); igual
     * criterio que el resto de features (Marriage, SmallGroup).
     */
    private UUID branchId;
}
