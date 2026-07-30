package pe.dcs.app.features.ministry_assignment.response;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class MinistryRoleSimpleResponse {

    private UUID roleId;

    private String roleName;

    private UUID ministryId;

    public MinistryRoleSimpleResponse() {
    }

    public MinistryRoleSimpleResponse(UUID roleId, String roleName, UUID ministryId) {
        this.roleId = roleId;
        this.roleName = roleName;
        this.ministryId = ministryId;
    }

}
