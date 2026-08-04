package pe.dcs.app.features.smallgroup.response;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.StatusType;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class SmallGroupDetailResponse {

    private UUID id;

    private String name;

    private String description;

    private UUID leaderPersonId;
    private String leaderName;
    private String leaderDni;
    private boolean leaderMember;

    private String meetingDay;

    private String meetingTime;

    private String location;

    private String category;

    private LocalDate startDate;
    private LocalDate endDate;
    private String topic;

    /**
     * Id del registro de servicio ministerial generado automáticamente
     * para el líder actual, si existe (ver
     * SmallGroupServiceImpl.syncLeaderMinistryService) — se puede ver
     * el detalle completo en Servicio Ministerial.
     */
    private UUID ministryAssignmentId;

    private StatusType status;

    private UUID branchId;
    private String branchName;

    private boolean canManage;

    private List<SmallGroupMemberResponse> members;
}
