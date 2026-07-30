package pe.dcs.app.features.user.org_user.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
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

    private String address;

    private LocalDate dateBirth;

    private String maritalStatus;

    private Integer children;

    private LocalDate dateAdmission;

    private UUID activeBranchId;

    private String activeBranchName;

}
