package pe.dcs.app.features.user.org_admin_branch.response;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.MaritalStatusType;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class OrgAdminBranchDetailResponse {
    private UUID id;
    private String name;
    private String lastname;
    private String sex;
    private String phone;
    private String dni;
    private MaritalStatusType maritalStatus;
    private Integer children;
    private String address;
    private LocalDate dateBirth;
    private String username;
}