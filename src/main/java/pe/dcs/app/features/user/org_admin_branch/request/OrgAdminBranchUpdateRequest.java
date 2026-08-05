package pe.dcs.app.features.user.org_admin_branch.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.MaritalStatusType;

import java.time.LocalDate;

@Getter
@Setter
public class OrgAdminBranchUpdateRequest {

    @NotBlank(message = "{error.elNombreEsObligatorio}")
    private String name;

    @NotBlank(message = "{error.elApellidoEsObligatorio}")
    private String lastname;

    @NotBlank(message = "{error.sexoObligatorio}")
    private String sex;

    @NotBlank(message = "{error.elDniEsObligatorio}")
    private String dni;

    private String phone;

    private String address;

    private LocalDate dateBirth;

    private MaritalStatusType maritalStatus;

    private Integer children;

    @NotBlank(message = "{error.usernameObligatorio}")
    private String username;

}