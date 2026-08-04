package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.auditable.Auditable;
import pe.dcs.app.util.enums.StatusType;

import java.util.UUID;

/**
 * Curso individual de la Academia Bíblica — exactamente uno de los
 * dos casos, nunca ambos (ver BibleAcademyServiceImpl.validateCourseForm):
 *
 * - Nivel de malla: {@code curriculum} no nulo + {@code order} (su
 *   posición dentro de esa malla, ver BibleCurriculum). Lo crea solo
 *   el org admin — es compartido por todas las sedes.
 * - Curso extra: {@code curriculum} nulo + {@code branch} no nulo.
 *   Lo crea el branch admin/delegado de esa sede, exclusivo de ella
 *   (no lo ve ni lo puede dictar otra sede) — sin prerequisito.
 */
@Entity
@Table(
        name = "bible_courses",
        indexes = {
                @Index(name = "idx_bible_course_curriculum", columnList = "curriculum_id"),
                @Index(name = "idx_bible_course_branch", columnList = "branch_id")
        }
)
@Getter
@Setter
public class BibleCourse extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    /** No nulo = curso de malla. Nulo = curso extra (ver branch). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "curriculum_id")
    private BibleCurriculum curriculum;

    /** Posición dentro de la malla — obligatorio y único por curriculum cuando curriculum != null. */
    @Column(name = "course_order")
    private Integer order;

    /** No nulo = curso extra exclusivo de esta sede. Nulo cuando el curso pertenece a una malla. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusType status;

    public boolean isExtra() {
        return curriculum == null;
    }
}
