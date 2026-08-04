package pe.dcs.app.features.smallgroup.response;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.auditable.AuditableResponse;
import pe.dcs.app.util.enums.StatusType;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class SmallGroupSearchRowResponse extends AuditableResponse {

    private UUID id;

    private String name;

    private String leaderName;

    private String meetingDay;

    private String meetingTime;

    private String location;

    private String category;

    private LocalDate startDate;
    private LocalDate endDate;

    private StatusType status;

    private UUID branchId;
    private String branchName;

    private long memberCount;

    private boolean canManage;
}
