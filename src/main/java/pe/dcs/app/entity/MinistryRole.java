package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.auditable.Auditable;

import java.util.UUID;

@Entity
@Table(name = "ministry_roles")
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


    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ministry_id", nullable = false)
    private Ministry ministry;


    private Boolean requiresActiveMembership = true;

}