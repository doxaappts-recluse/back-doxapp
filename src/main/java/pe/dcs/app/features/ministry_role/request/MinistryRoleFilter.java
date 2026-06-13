package pe.dcs.app.features.ministry_role.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MinistryRoleFilter {
    private String name;
    private Boolean active;
}