package pe.dcs.app.features.branch.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class BranchUpdateRequest {

    @NotBlank(message = "{error.sedeNombreObligatorio}")
    private String name;

    @NotBlank(message = "{error.sedeCodigoObligatorio}")
    private String code;

    @NotBlank(message = "{error.sedeDireccionObligatoria}")
    private String address;

    private String phone;
    private String email;

    @NotNull(message = "{error.sedeFechaAperturaObligatoria}")
    private LocalDate openingDate;
}