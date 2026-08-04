package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.auditable.Auditable;
import pe.dcs.app.util.enums.bible_academy.BibleEnrollmentStatus;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Matrícula de una Person a un {@link BibleClass} puntual. status
 * APPROVED es lo único que satisface el prerequisito del siguiente
 * nivel de una malla (ver BibleAcademyServiceImpl.hasApprovedPrerequisite)
 * y habilita el certificado de finalización.
 *
 * statusReason es obligatorio (a nivel de servicio, no de columna)
 * cuando status es FAILED o WITHDRAWN — pedido explícito del usuario
 * al definir el flujo.
 *
 * prerequisiteOverridden + overrideReason: en vez de una pantalla de
 * "migrar malla vieja a malla nueva", un admin (branch/org, nunca un
 * delegado) puede saltarse el prerequisito de forma manual dejando
 * un motivo obligatorio — cubre migración de malla y cualquier otro
 * caso (alumno formado en otra iglesia, etc.) con un solo mecanismo.
 */
@Entity
@Table(
        name = "bible_enrollments",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_bible_enrollment_class_person",
                        columnNames = {"bible_class_id", "person_id"}
                )
        },
        indexes = {
                @Index(name = "idx_bible_enrollment_class", columnList = "bible_class_id"),
                @Index(name = "idx_bible_enrollment_person", columnList = "person_id"),
                @Index(name = "idx_bible_enrollment_person_status", columnList = "person_id,status")
        }
)
@Getter
@Setter
public class BibleEnrollment extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bible_class_id", nullable = false)
    private BibleClass bibleClass;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    @Column(name = "enroll_date", nullable = false)
    private LocalDate enrollDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BibleEnrollmentStatus status;

    @Column(name = "final_grade")
    private Integer finalGrade;

    /** Motivo — obligatorio (a nivel servicio) cuando status es FAILED o WITHDRAWN. */
    @Column(name = "status_reason", length = 1000)
    private String statusReason;

    @Column(name = "prerequisite_overridden", nullable = false)
    private boolean prerequisiteOverridden;

    @Column(name = "override_reason", length = 1000)
    private String overrideReason;
}
