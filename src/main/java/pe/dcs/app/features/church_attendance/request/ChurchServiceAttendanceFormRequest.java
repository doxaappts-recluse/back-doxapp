package pe.dcs.app.features.church_attendance.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class ChurchServiceAttendanceFormRequest {

    /**
     * Obligatorio — Asistencia a Cultos siempre opera sobre una
     * Person que ya existe, encontrada por DNI (ver
     * ChurchServiceController.findPersonByDni). No admite invitados
     * de solo nombre en esta primera versión.
     */
    @NotNull(message = "{error.debeSeleccionarPersonaAsistio}")
    private UUID personId;

    private LocalDate attendanceDate;

    private String observations;
}
