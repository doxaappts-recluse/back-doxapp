package pe.dcs.app.features.ministry_role.request;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class MinistryRoleRequest {
    private String name;
    private String description;

    // null = global role
    private UUID ministryId;

    // opcional, pero si no lo envías debería tener default en backend
    private Boolean requiresActiveMembership;
}