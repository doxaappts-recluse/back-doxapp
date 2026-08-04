package pe.dcs.app.features.bible_academy.response;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.auditable.AuditableResponse;
import pe.dcs.app.util.enums.bible_academy.BibleClassStatus;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class BibleClassSearchRowResponse extends AuditableResponse {

    private UUID id;

    private UUID courseId;
    private String courseName;
    private String curriculumName;
    private boolean courseExtra;

    private UUID branchId;
    private String branchName;

    private UUID teacherPersonId;
    private String teacherName;

    private String meetingDay;
    private String meetingTime;
    private String location;

    private LocalDate startDate;
    private LocalDate endDate;

    private Integer capacity;
    private long enrolledCount;

    private BibleClassStatus status;

    private boolean canManage;
}
