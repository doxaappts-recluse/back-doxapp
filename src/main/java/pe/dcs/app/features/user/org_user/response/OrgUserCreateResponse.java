package pe.dcs.app.features.user.org_user.response;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class OrgUserCreateResponse {
    private UUID id;
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
