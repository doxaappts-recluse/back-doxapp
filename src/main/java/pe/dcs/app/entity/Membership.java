package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.Auditable;
import pe.dcs.app.util.enums.membership.MembershipExitReason;
import pe.dcs.app.util.enums.membership.MembershipStatus;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
        name = "memberships",
        indexes = {
                @Index(name = "idx_membership_user", columnList = "user_id"),
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
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MembershipStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MembershipExitReason exitReason;

    private String reason;

    @Column
    private Boolean current;

    @Column(length = 1000)
    private String notes;
}