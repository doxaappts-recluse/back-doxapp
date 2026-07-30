package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.auditable.Auditable;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Periodo en el que una persona sirve con un rol dentro de un
 * ministerio. Es un registro histórico: una persona puede servir
 * muchas veces en el mismo ministerio (varios periodos), pero
 * esos periodos no pueden solaparse en fechas entre sí. No hay
 * restricción alguna entre ministerios distintos (puede servir
 * en varios ministerios a la vez).
 *
 * endDate = null significa que el servicio sigue vigente.
 */
@Entity
@Table(
        name = "ministry_assignments",
        indexes = {
                @Index(name = "idx_ministry_assignment_person", columnList = "person_id"),
                @Index(name = "idx_ministry_assignment_ministry", columnList = "ministry_id"),
                @Index(name = "idx_ministry_assignment_person_ministry", columnList = "person_id,ministry_id")
        }
)
@Getter
@Setter
public class MinistryAssignment extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ministry_id", nullable = false)
    private Ministry ministry;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ministry_role_id", nullable = false)
    private MinistryRole ministryRole;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(length = 500)
    private String reason;

    @Column(length = 500)
    private String observation;

}
