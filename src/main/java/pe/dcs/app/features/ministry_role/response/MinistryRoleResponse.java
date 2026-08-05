package pe.dcs.app.features.ministry_role.response;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.StatusType;

import java.util.UUID;

@Getter
@Setter
public class MinistryRoleResponse {
    private UUID id;
    private String code;
    private String name;
    private String nameEs;
    private String nameEn;
    private String description;
    private StatusType status;
    private UUID ministryId;
    private Boolean requiresActiveMembership;
}