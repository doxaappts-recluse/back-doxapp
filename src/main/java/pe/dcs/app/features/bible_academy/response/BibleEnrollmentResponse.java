package pe.dcs.app.features.bible_academy.response;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.auditable.AuditableResponse;
import pe.dcs.app.util.enums.bible_academy.BibleEnrollmentStatus;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class BibleEnrollmentResponse extends AuditableResponse {

    private UUID id;

    private UUID bibleClassId;

    private UUID personId;
    private String personName;
    private String personLastname;
    private String personDni;

    private LocalDate enrollDate;

    private BibleEnrollmentStatus status;

    private Integer finalGrade;

    private String statusReason;

    private boolean prerequisiteOverridden;
    private String overrideReason;

    private boolean canManage;
}
