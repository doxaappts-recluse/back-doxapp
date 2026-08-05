package pe.dcs.app.features.bible_academy.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.bible_academy.BibleEnrollmentStatus;

@Getter
@Setter
public class BibleEnrollmentStatusUpdateRequest {

    @NotNull(message = "{error.elEstadoEsObligatorio}")
    private BibleEnrollmentStatus status;

    private Integer finalGrade;

    /** Obligatorio (validado en servicio) cuando status es FAILED o WITHDRAWN. */
    private String statusReason;
}
