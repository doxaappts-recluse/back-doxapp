package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.auditable.Auditable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "ministries")
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

    @Column(nullable = false)
    private Boolean active = true;


    @OneToMany(
            mappedBy = "ministry",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<MinistryRole> roles = new ArrayList<>();

}