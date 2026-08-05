package pe.dcs.app.features.user.org_admin_branch.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.MaritalStatusType;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class OrgAdminBranchCreateRequest {

    // ============================
    // PERSON
    // ============================

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

    // ============================
    // CREDENTIAL
    // ============================

    @NotBlank(message = "{error.usernameObligatorio}")
    private String username;

    @NotBlank(message = "{error.passwordObligatorio}")
    private String password;

    // ============================
    // ACCESS
    // ============================

    @NotNull(message = "{error.laOrganizacionEsObligatoria}")
    private UUID organizationId;

    private UUID branchId;

    @NotNull(message = "{error.elRolEsObligatorio}")
    private UUID roleId;

    // ============================
    // PERSON BRANCH
    // ============================

    @NotNull(message = "{error.fechaInicioObligatoria}")
    private LocalDate startDate;

}