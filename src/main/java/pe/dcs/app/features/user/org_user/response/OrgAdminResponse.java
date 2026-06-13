package pe.dcs.app.features.user.org_user.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
public class OrgAdminResponse {

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

    private String username;
    private String password;

    private UUID organizationId;

}