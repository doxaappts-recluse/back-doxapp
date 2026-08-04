package pe.dcs.app.features.bible_academy.response;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.bible_academy.BibleClassStatus;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class BibleClassDetailResponse {

    private UUID id;

    private UUID courseId;
    private String courseName;
    private UUID curriculumId;
    private String curriculumName;
    private Integer curriculumOrder;
    private boolean courseExtra;

    private UUID branchId;
    private String branchName;

    private UUID teacherPersonId;
    private String teacherName;
    private String teacherDni;

    private String meetingDay;
    private String meetingTime;
    private String location;

    private LocalDate startDate;
    private LocalDate endDate;

    private Integer capacity;
    private long enrolledCount;

    private BibleClassStatus status;

    /** Servicio ministerial generado para el maestro actual, si existe — ver BibleAcademyServiceImpl.syncTeacherMinistryService. */
    private UUID ministryAssignmentId;

    private boolean canManage;
}
