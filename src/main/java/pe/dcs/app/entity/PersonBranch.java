package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.auditable.Auditable;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
        name = "person_branches",
        indexes = {
                @Index(name = "idx_person_branch_person", columnList = "person_id"),
                @Index(name = "idx_person_branch_branch", columnList = "branch_id"),
                @Index(name = "idx_person_branch_person_active", columnList = "person_id,active"),
                @Index(name = "idx_person_branch_branch_active", columnList = "branch_id,active")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_person_branch_period",
                        columnNames = {
                                "person_id",
                                "branch_id",
                                "start_date"
                        }
                )
        }
)
@Getter
@Setter
public class PersonBranch extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    /**
     * Persona.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    /**
     * Sede donde pertenece.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    /**
     * Indica la sede actual de la persona.
     * Solo debe existir un registro activo.
     */
    @Column(nullable = false)
    private Boolean active = true;

    /**
     * Fecha de inicio en la sede.
     */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /**
     * Fecha de salida de la sede.
     * Null mientras permanezca en ella.
     */
    @Column(name = "end_date")
    private LocalDate endDate;

    /**
     * Motivo del traslado.
     */
    @Column(length = 500)
    private String transferReason;

}