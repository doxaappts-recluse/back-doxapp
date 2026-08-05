package pe.dcs.app.features.organization.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class OrganizationUpdateRequest {

    @NotBlank(message = "{error.organizacionNombreObligatorio}")
    private String name;

    @NotBlank(message = "{error.organizacionDireccionObligatoria}")
    private String address;

    @NotBlank(message = "{error.organizacionRucObligatorio}")
    private String ruc;

    @NotBlank(message = "{error.organizacionEmailObligatorio}")
    @Email(message = "{error.organizacionEmailInvalido}")
    private String email;

    @NotNull(message = "{error.organizacionFechaFundacionObligatoria}")
    private LocalDate foundedDate;
}
