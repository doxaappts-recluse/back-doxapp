package pe.dcs.app.features.church_attendance.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.StatusType;

import java.util.UUID;

@Getter
@Setter
public class ChurchServiceFilterRequest {

    private String name;

    private StatusType status;

    /**
     * Solo relevante para org admin/SYSTEM; para branch admin/org
     * user delegado el scope ya lo fija ChurchServiceSpecification
     * con la sede/permiso actual.
     */
    private UUID branchId;
}
