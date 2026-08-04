package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.auditable.Auditable;
import pe.dcs.app.util.enums.bible_academy.BibleClassStatus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Dictado — cómo una sede puntual ofrece un {@link BibleCourse}
 * (sea de malla o extra): maestro, horario, fechas, cupo. Cada sede
 * elige su propio maestro para su propio dictado, aunque el curso
 * sea compartido a nivel malla (ver diseño acordado con el usuario).
 *
 * El maestro vinculado a una Person con sede activa genera
 * automáticamente un servicio ministerial — mismo patrón que
 * SmallGroup.leaderPerson / SmallGroupServiceImpl.syncLeaderMinistryService.
 */
@Entity
@Table(
        name = "bible_classes",
        indexes = {
                @Index(name = "idx_bible_class_course", columnList = "course_id"),
                @Index(name = "idx_bible_class_branch", columnList = "branch_id"),
                @Index(name = "idx_bible_class_status", columnList = "status")
        }
)
@Getter
@Setter
public class BibleClass extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private BibleCourse course;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    /** Maestro vinculado a una Person, opcional — igual criterio que SmallGroup.leaderPerson/leaderName. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_person_id")
    private Person teacherPerson;

    @Column(name = "teacher_name")
    private String teacherName;

    @Column(name = "meeting_day")
    private String meetingDay;

    @Column(name = "meeting_time")
    private String meetingTime;

    private String location;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    private Integer capacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BibleClassStatus status;

    /**
     * Servicio ministerial generado automáticamente para el maestro
     * actual mientras dure el dictado — null si no hay maestro
     * vinculado a una Person con sede activa. Ver
     * BibleAcademyServiceImpl.syncTeacherMinistryService.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ministry_assignment_id")
    private MinistryAssignment ministryAssignment;

    @OneToMany(
            mappedBy = "bibleClass",
            fetch = FetchType.LAZY
    )
    private List<BibleEnrollment> enrollments = new ArrayList<>();
}
