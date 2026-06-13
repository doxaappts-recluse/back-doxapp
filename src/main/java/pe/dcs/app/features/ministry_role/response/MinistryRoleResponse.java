package pe.dcs.app.features.ministry_role.response;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class MinistryRoleResponse {

    private UUID id;
    private String name;
    private String description;
    private Boolean active;

    // null = rol global
    private UUID ministryId;

    private Boolean requiresActiveMembership;
}