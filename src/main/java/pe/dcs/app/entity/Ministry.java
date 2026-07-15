package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.auditable.Auditable;
import pe.dcs.app.util.enums.StatusType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "ministries",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_ministry_name",
                        columnNames = "name"
                )
        }
)
@Getter
@Setter
public class Ministry extends Auditable {

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

    @OneToMany(
            mappedBy = "ministry",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<MinistryRole> roles = new ArrayList<>();

    @Column(nullable = false)
    private Boolean requiresActiveMembership = true;

}