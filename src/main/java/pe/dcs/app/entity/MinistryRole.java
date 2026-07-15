package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.auditable.Auditable;
import pe.dcs.app.util.enums.StatusType;

import java.util.UUID;

@Entity
@Table(
        name = "ministry_roles",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_ministry_role_name",
                        columnNames = {
                                "ministry_id",
                                "name"
                        }
                )
        }
)
@Getter
@Setter
public class MinistryRole extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusType status = StatusType.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ministry_id", nullable = false)
    private Ministry ministry;

    @Column(nullable = false)
    private Boolean requiresActiveMembership = true;

}