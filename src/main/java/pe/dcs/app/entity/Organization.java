package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import pe.dcs.app.util.auditable.Auditable;
import pe.dcs.app.util.enums.StatusType;

@Entity
@Table(
        name = "organizations",
        indexes = {
                @Index(
                        name = "idx_org_name",
                        columnList = "name"
                ),
                @Index(
                        name = "idx_org_ruc",
                        columnList = "ruc"
                ),
                @Index(
                        name = "idx_org_status",
                        columnList = "status"
                )
        }
)
@Getter
@Setter
public class Organization extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(
            nullable = false,
            length = 150
    )
    private String name;

    @Column(
            nullable = false,
            unique = true,
            length = 20
    )
    private String ruc;

    private String address;

    @Column(nullable = false)
    private LocalDate foundedDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusType status;

    // =========================
    // BRANCHES
    // =========================

    /**
     * Sedes pertenecientes
     * a la organización.
     */
    @OneToMany(
            mappedBy = "organization",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<Branch> branches =
            new ArrayList<>();

    // =========================
    // ACCESS
    // =========================

    /**
     * Accesos administrativos
     * dentro de la organización.
     */
    @OneToMany(
            mappedBy = "organization"
    )
    private List<UserAccess> userAccesses =
            new ArrayList<>();

    public void disable(boolean hasActiveContracts) {

        if(status == StatusType.INACTIVE){
            return;
        }

        if(hasActiveContracts){
            throw new IllegalStateException(
                    "No se puede deshabilitar la organización porque posee sedes con contratos activos."
            );
        }

        status = StatusType.INACTIVE;
    }

    public void enable(){

        if(status == StatusType.ACTIVE){
            return;
        }

        status = StatusType.ACTIVE;
    }

}