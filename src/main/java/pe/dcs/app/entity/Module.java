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
        name = "modules",
        indexes = {

                @Index(
                        name = "idx_module_code",
                        columnList = "code"
                ),

                @Index(
                        name = "idx_module_status",
                        columnList = "status"
                ),

                @Index(
                        name = "idx_module_parent",
                        columnList = "parent_id"
                )

        }
)
@Getter
@Setter
public class Module extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    // =========================
    // INFORMATION
    // =========================

    @Column(nullable = false)
    private String name;

    @Column(
            nullable = false,
            unique = true
    )
    private String code;

    private String icon;

    private String route;

    private Integer orderNum;


    // =========================
    // STATUS
    // =========================

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusType status;

    // =========================
    // TREE STRUCTURE
    // =========================

    /**
     * Módulo padre.
     *
     * Ejemplo:
     *
     * Usuarios
     *    |
     *    +-- Lista
     *    +-- Crear
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Module parent;

    /**
     * Hijos del módulo.
     */
    @OneToMany(
            mappedBy = "parent",
            fetch = FetchType.LAZY
    )
    private List<Module> children =
            new ArrayList<>();

    // =========================
    // HELPERS
    // =========================

    public boolean isActive(){
        return status == StatusType.ACTIVE;
    }

    public boolean isRoot(){
        return parent == null;
    }

}