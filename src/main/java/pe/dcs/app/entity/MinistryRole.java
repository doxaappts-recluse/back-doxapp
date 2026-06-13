package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.Auditable;

import java.util.UUID;

@Entity
@Table(
        name = "ministry_roles",
        indexes = {
                @Index(
                        name = "idx_ministry_role_name",
                        columnList = "name"
                ),
                @Index(
                        name = "idx_ministry_role_ministry",
                        columnList = "ministry_id"
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

    @Column(nullable = false)
    private Boolean active = true;

    /* null = rol global */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ministry_id")
    private Ministry ministry;

    @Column(name = "requires_active_membership")
    private Boolean requiresActiveMembership = true;
}