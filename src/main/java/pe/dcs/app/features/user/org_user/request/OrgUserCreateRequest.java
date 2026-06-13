package pe.dcs.app.features.user.org_user.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrgUserCreateRequest {

    private String name;
    private String lastname;
    private String dni;
    private String sex;
    private String phone;
    private String address;
    private String dateBirth;
    private String maritalStatus;
    private String children;
    private String dateAdmission;
}