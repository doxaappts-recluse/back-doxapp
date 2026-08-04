package pe.dcs.app.features.familygroup.response;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.auditable.AuditableResponse;
import pe.dcs.app.util.enums.StatusType;

import java.util.UUID;

@Getter
@Setter
public class FamilyGroupSearchRowResponse extends AuditableResponse {

    private UUID id;

    private String name;

    private String headName;

    private long memberCount;

    private StatusType status;

    private UUID branchId;
    private String branchName;

    private boolean canManage;
}
