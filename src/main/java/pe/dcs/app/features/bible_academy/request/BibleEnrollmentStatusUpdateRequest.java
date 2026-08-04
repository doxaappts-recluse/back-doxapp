package pe.dcs.app.features.bible_academy.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.bible_academy.BibleEnrollmentStatus;

@Getter
@Setter
public class BibleEnrollmentStatusUpdateRequest {

    private BibleEnrollmentStatus status;

    private Integer finalGrade;

    /** Obligatorio (validado en servicio) cuando status es FAILED o WITHDRAWN. */
    private String statusReason;
}
