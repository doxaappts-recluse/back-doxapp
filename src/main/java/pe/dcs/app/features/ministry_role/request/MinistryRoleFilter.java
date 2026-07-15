package pe.dcs.app.features.ministry_role.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.StatusType;

@Getter
@Setter
public class MinistryRoleFilter {
    private String name;
    private StatusType active;
}