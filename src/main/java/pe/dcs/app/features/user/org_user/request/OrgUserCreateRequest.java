package pe.dcs.app.features.user.org_user.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.MaritalStatusType;

import java.time.LocalDate;

/**
 * Crea una Person (congregante) dentro de la organización/sede
 * actual del contexto de quien hace la petición (ORG_ADMIN o
 * ORG_BRANCH_ADMIN). No maneja credenciales de acceso al sistema.
 */
@Getter
@Setter
public class OrgUserCreateRequest {

    @NotBlank(message = "{error.personaNombreObligatorio}")
    private String name;

    @NotBlank(message = "{error.personaApellidoObligatorio}")
    private String lastname;

    @NotBlank(message = "{error.personaDniObligatorio}")
    private String dni;

    private String sex;

    private String phone;

    private String address;

    private LocalDate dateBirth;

    private MaritalStatusType maritalStatus;

    private Integer children;

    private LocalDate dateAdmission;

}
