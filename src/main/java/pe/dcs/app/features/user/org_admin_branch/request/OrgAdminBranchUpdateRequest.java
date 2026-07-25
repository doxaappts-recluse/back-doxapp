package pe.dcs.app.features.user.org_admin_branch.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.MaritalStatusType;

import java.time.LocalDate;

@Getter
@Setter
public class OrgAdminBranchUpdateRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String lastname;

    @NotBlank
    private String sex;

    @NotBlank
    private String dni;

    private String phone;

    private String address;

    private LocalDate dateBirth;

    private MaritalStatusType maritalStatus;

    private Integer children;

    @NotBlank
    private String username;

}