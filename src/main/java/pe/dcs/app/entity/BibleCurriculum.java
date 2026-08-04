package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.auditable.Auditable;
import pe.dcs.app.util.enums.bible_academy.BibleCurriculumStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Malla curricular de la Academia Bíblica — a nivel organización
 * (compartida entre todas las sedes), la crea y gestiona solo el
 * org admin (ver BibleAcademyAccessGuard, sin bypass SYSTEM). Cada
 * {@link BibleCourse} que cuelga de esta malla (curriculum != null)
 * representa un nivel ordenado (order) con progresión obligatoria —
 * ver BibleAcademyServiceImpl.assertPrerequisiteApproved.
 *
 * Solo puede haber UNA malla ACTIVE por organización a la vez —
 * activar una malla nueva retira automáticamente la anterior (mismo
 * criterio que Contract.markReplaced, pero sin fechas/scheduler: acá
 * no hay vigencia temporal, solo un estado).
 */
@Entity
@Table(
        name = "bible_curriculums",
        indexes = {
                @Index(name = "idx_bible_curriculum_org", columnList = "organization_id"),
                @Index(name = "idx_bible_curriculum_org_status", columnList = "organization_id,status")
        }
)
@Getter
@Setter
public class BibleCurriculum extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BibleCurriculumStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @OneToMany(
            mappedBy = "curriculum",
            fetch = FetchType.LAZY
    )
    private List<BibleCourse> courses = new ArrayList<>();
}
