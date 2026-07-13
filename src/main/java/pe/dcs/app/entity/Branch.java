package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.auditable.Auditable;
import pe.dcs.app.util.enums.StatusType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "branches",
        indexes = {

                @Index(
                        name = "idx_branch_org",
                        columnList = "organization_id"
                )

        }
)
@Getter
@Setter
public class Branch extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String code;

    private String address;

    private String phone;

    private String email;

    @Column(nullable = false)
    private LocalDate openingDate;
    
    @Column(
            name = "is_main",
            nullable = false
    )
    private Boolean main;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusType status;

    // =========================
    // ORGANIZATION
    // =========================

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "organization_id",
            nullable = false
    )
    private Organization organization;

    @OneToMany(mappedBy = "branch")
    private List<PersonBranch> personBranches = new ArrayList<>();

    // =========================
    // ACCESS
    // =========================

    /**
     * Usuarios que tienen acceso
     * administrativo a esta sede.
     */
    @OneToMany(
            mappedBy = "branch"
    )
    private List<UserAccess> userAccesses =
            new ArrayList<>();

    // =========================
    // CONTRACTS
    // =========================

    /**
     * Contratos asociados
     * a esta sede.
     *
     * Una sede puede tener
     * historial de contratos.
     */
    @OneToMany(
            mappedBy = "branch",
            fetch = FetchType.LAZY
    )
    private List<Contract> contracts =
            new ArrayList<>();

    public void disable() {

        if(status == StatusType.INACTIVE){
            return;
        }

        status = StatusType.INACTIVE;
    }

    public void enable() {

        if(status == StatusType.ACTIVE){
            return;
        }

        status = StatusType.ACTIVE;
    }

}