package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.Auditable;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
        name = "member_ministry_assignments",
        indexes = {
                @Index(name = "idx_mma_user", columnList = "user_id"),
                @Index(name = "idx_mma_ministry", columnList = "ministry_id")
        }
)
@Getter
@Setter
public class MemberMinistryAssignment extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ministry_id", nullable = false)
    private Ministry ministry;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ministry_role_id")
    private MinistryRole ministryRole;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "reason")
    private String reason   ;

    @Column(name = "observation")
    private String observation;

}