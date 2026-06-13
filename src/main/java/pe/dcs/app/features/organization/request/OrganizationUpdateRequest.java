package pe.dcs.app.features.organization.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrganizationUpdateRequest {
    private String name;
    private String address;
    private String ruc;
}
