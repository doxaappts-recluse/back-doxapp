package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.auditable.Auditable;
import pe.dcs.app.util.enums.StatusType;

import java.util.UUID;

/**
 * Espacio físico reservable de una sede (salón, auditorio, aula,
 * etc.). Catálogo administrado por el branch admin de su propia sede
 * (org admin ve/gestiona todas) — ver SpaceReservationAccessGuard.
 */
@Entity
@Table(
        name = "reservable_spaces",
        indexes = {
                @Index(name = "idx_reservable_space_branch", columnList = "branch_id")
        }
)
@Getter
@Setter
public class ReservableSpace extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    private Integer capacity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusType status;
}
