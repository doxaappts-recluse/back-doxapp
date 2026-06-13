package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

import pe.dcs.app.util.Auditable;
import pe.dcs.app.util.enums.StatusType;

@Entity
@Table(
        name = "organizations",
        indexes = {
                @Index(name = "idx_org_name", columnList = "name"),
                @Index(name = "idx_org_status", columnList = "status")
        }
)
@Getter
@Setter
public class Organization extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id")
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String ruc;

    private String address;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private StatusType status;

    @OneToOne
    private Contract activeContract;
}