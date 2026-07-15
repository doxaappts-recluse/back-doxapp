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
        name = "member_ministry_assignments",
        indexes = {
                @Index(name = "idx_assignment_person", columnList = "person_id"),
                @Index(name = "idx_assignment_ministry", columnList = "ministry_id")
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
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ministry_id", nullable = false)
    private Ministry ministry;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ministry_role_id", nullable = false)
    private MinistryRole ministryRole;

    private LocalDate startDate;

    private LocalDate endDate;

    private String reason;

    private String observation;

    @Column(nullable = false)
    private Boolean active = true;

}