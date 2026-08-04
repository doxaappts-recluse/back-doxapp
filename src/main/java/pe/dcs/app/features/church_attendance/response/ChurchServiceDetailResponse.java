package pe.dcs.app.features.church_attendance.response;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.StatusType;

import java.util.UUID;

@Getter
@Setter
public class ChurchServiceDetailResponse {

    private UUID id;

    private String name;

    private String dayOfWeek;

    private String timeOfDay;

    private StatusType status;

    private UUID branchId;
    private String branchName;

    private boolean canManage;
}
