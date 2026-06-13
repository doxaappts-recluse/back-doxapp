package pe.dcs.app.features.organization.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.StatusType;

@Getter
@Setter
public class OrganizationFilter {
    private String name;
    private String address;
    private StatusType status;
    private String ruc;
}
