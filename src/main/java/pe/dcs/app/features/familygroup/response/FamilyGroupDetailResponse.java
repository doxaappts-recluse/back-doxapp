package pe.dcs.app.features.familygroup.response;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.StatusType;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class FamilyGroupDetailResponse {

    private UUID id;

    private String name;

    private String observations;

    private StatusType status;

    private UUID branchId;
    private String branchName;

    private boolean canManage;

    private List<FamilyMemberResponse> members;
}
