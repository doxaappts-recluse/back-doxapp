package pe.dcs.app.features.user.system_user.request;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class UserSystemCreateRequest {
    private String name;
    private String lastname;
    private String username;
    private String dni;
    private String sex;
    private String phone;
    private String address;
    private String dateBirth;
    private String maritalStatus;
    private String children;
    private String dateAdmission;
    private UUID rolId;
}
