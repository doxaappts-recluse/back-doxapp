package pe.dcs.app.features.user.system_user.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.MaritalStatusType;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class UserSystemCreateRequest {

    @NotBlank(message = "{error.elNombreEsObligatorio}")
    private String name;

    @NotBlank(message = "{error.elApellidoEsObligatorio}")
    private String lastname;

    @NotBlank(message = "{error.usernameObligatorio}")
    private String username;

    @NotBlank(message = "{error.elDniEsObligatorio}")
    private String dni;

    @NotBlank(message = "{error.sexoObligatorio}")
    private String sex;

    private String phone;

    private String address;

    private LocalDate dateBirth;

    private MaritalStatusType maritalStatus;

    private Integer children;

    private LocalDate dateAdmission;

    @NotNull(message = "{error.elRolEsObligatorio}")
    private UUID roleId;
}