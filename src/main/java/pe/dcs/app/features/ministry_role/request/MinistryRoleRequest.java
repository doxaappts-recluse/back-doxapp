package pe.dcs.app.features.ministry_role.request;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class MinistryRoleRequest {
    private String name;
    private String description;
    private UUID ministryId;
    private Boolean requiresActiveMembership;
}