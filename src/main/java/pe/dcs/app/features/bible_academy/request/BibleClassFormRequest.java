package pe.dcs.app.features.bible_academy.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.bible_academy.BibleClassStatus;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class BibleClassFormRequest {

    private UUID courseId;

    /** Solo relevante para org admin (elige sede libremente); igual criterio que el resto de features. */
    private UUID branchId;

    /** Setear solo si el maestro se encontró por DNI. Si es null, queda solo teacherName en texto libre. */
    private UUID teacherPersonId;
    private String teacherName;
    private String teacherDni;

    private String meetingDay;

    private String meetingTime;

    private String location;

    private LocalDate startDate;
    private LocalDate endDate;

    private Integer capacity;

    private BibleClassStatus status;
}
