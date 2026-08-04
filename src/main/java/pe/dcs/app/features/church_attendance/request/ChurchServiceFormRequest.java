package pe.dcs.app.features.church_attendance.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.StatusType;

import java.util.UUID;

@Getter
@Setter
public class ChurchServiceFormRequest {

    private String name;

    private String dayOfWeek;

    private String timeOfDay;

    private StatusType status;

    /**
     * Solo relevante para org admin (elige sede libremente); igual
     * criterio que el resto de features (Marriage, SmallGroup).
     */
    private UUID branchId;
}
