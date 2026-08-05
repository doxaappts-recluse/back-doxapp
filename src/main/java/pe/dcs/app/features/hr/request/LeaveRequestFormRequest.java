package pe.dcs.app.features.hr.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.hr.HrLeaveType;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class LeaveRequestFormRequest {

    @NotNull(message = "{error.empleadoObligatorio}")
    private UUID staffId;

    @NotNull(message = "{error.tipoSolicitudObligatorio}")
    private HrLeaveType type;

    @NotNull(message = "{error.fechaInicioObligatoria}")
    private LocalDate startDate;

    @NotNull(message = "{error.fechaFinObligatoria}")
    private LocalDate endDate;

    private String reason;
}
