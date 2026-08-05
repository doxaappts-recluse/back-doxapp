package pe.dcs.app.features.baptism.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class BaptismFormRequest {

    @NotNull(message = "{error.fechaBautizoObligatoria}")
    private LocalDate baptismDate;

    @NotBlank(message = "{error.iglesiaDondeBautizoObligatoria}")
    private String churchName;

    private String pastorName;

    private String city;

    private boolean verified;

    private String observations;
}
