package pe.dcs.app.features.ministry_assignment.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class MinistryAssignmentUpdateRequest {

    @NotNull(message = "{error.fechaInicioObligatoria}")
    private LocalDate startDate;

    // fecha de fin: opcional (asignación en curso si se omite)
    private LocalDate endDate;

    // motivo/observación: opcionales, no se validan en el servicio
    private String reason;

    private String observation;

}
