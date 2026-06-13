package pe.dcs.app.features.user.org_user.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrgUserFilterRequest {

    private String name;

    private String lastname;

    private String sex;

    private String dni;
}