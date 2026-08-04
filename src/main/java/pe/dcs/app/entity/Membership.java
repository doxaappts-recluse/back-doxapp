package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.auditable.Auditable;
import pe.dcs.app.util.enums.membership.MembershipExitReason;
import pe.dcs.app.util.enums.membership.MembershipReason;
import pe.dcs.app.util.enums.StatusType;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
        name = "memberships",
        indexes = {
                @Index(name = "idx_membership_person", columnList = "person_id"),
                @Index(name = "idx_membership_status", columnList = "status")
        }
)
@Getter
@Setter
public class Membership extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="person_id")
    private Person person;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusType status;

    @Enumerated(EnumType.STRING)
    private MembershipExitReason exitReason;

    @Enumerated(EnumType.STRING)
    private MembershipReason reason;

    @Column
    private Boolean current;

    @Column(length = 1000)
    private String notes;

    /**
     * Sede desde la que se creó este registro (la sede activa de
     * la persona en ese momento). Se usa para saber, si la persona
     * se traslada de sede después, de quién es "dueño" este
     * registro a efectos de DataAccessRule/VisibilityGrant. Null
     * en registros previos a esta columna (se tratan como
     * visibles, sin restricción).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;
}