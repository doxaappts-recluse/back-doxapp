package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.Auditable;
import pe.dcs.app.util.enums.StatusType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "modules",
        indexes = {
                @Index(name = "idx_module_code", columnList = "code"),
                @Index(name = "idx_module_status", columnList = "status")
        }
)
@Getter
@Setter
public class Module extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id")
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "code",
            nullable = false,
            unique = true
    )
    private String code;

    @Column(name = "icon")
    private String icon;

    @Column(name = "route")
    private String route;

    @Column(name = "order_num")
    private Integer orderNum;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private StatusType status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Module parent;

    @OneToMany(mappedBy = "parent")
    private List<Module> children =
            new ArrayList<>();
}