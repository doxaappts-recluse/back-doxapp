package pe.dcs.app.features.user.org_user.response;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class OrgUserResponse {

    private UUID id;
    private String name;
    private String lastname;
    private String fullName;
    private String dni;
    private String sex;
    private String phone;
    private String dateBirth;
}