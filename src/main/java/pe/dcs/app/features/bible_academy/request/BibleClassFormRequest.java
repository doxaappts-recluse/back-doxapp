package pe.dcs.app.features.bible_academy.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.bible_academy.BibleClassStatus;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class BibleClassFormRequest {

    @NotNull(message = "{error.debeSeleccionarCursoDictado}")
    private UUID courseId;

    /** Solo relevante para org admin (elige sede libremente); igual criterio que el resto de features. */
    private UUID branchId;

    /** Setear solo si el maestro se encontró por DNI. Si es null, queda solo teacherName en texto libre. */
    private UUID teacherPersonId;
    private String teacherName;
    private String teacherDni;

    @NotBlank(message = "{error.diaReunionObligatorio}")
    private String meetingDay;

    @NotBlank(message = "{error.horaReunionObligatoria}")
    private String meetingTime;

    @NotBlank(message = "{error.lugarDictadoObligatorio}")
    private String location;

    @NotNull(message = "{error.fechaInicioDictadoObligatoria}")
    private LocalDate startDate;
    private LocalDate endDate;

    private Integer capacity;

    private BibleClassStatus status;
}
