package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.auditable.Auditable;
import pe.dcs.app.util.enums.contract.ContractRenewalType;
import pe.dcs.app.util.enums.contract.ContractStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "contracts",
        indexes = {
                @Index(
                        name = "idx_contract_branch",
                        columnList = "branch_id"
                ),
                @Index(
                        name = "idx_contract_status",
                        columnList = "status"
                ),
                @Index(
                        name = "idx_contract_branch_start_date",
                        columnList = "branch_id,start_date"
                ),
                @Index(
                        name = "idx_contract_branch_status",
                        columnList = "branch_id,status"
                )
        }
)
@Getter
@Setter
public class Contract extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    // =========================
    // PLAN
    // =========================

    private String planName;

    private Double price;

    private String currency;

    // =========================
    // VIGENCIA
    // =========================

    private LocalDate startDate;

    private LocalDate endDate;

    // =========================
    // CAPACIDAD
    // =========================

    private Integer numberUsers;

    // =========================
    // ESTADO
    // =========================

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContractStatus status;

    private Instant suspendedAt;

    private Instant cancelledAt;

    private Instant activatedAt;

    // =========================
    // CICLO DE VIDA
    // =========================

    private UUID previousContractId;

    @Enumerated(EnumType.STRING)
    private ContractRenewalType renewalType;

    // =========================
    // MODULES
    // =========================

    @OneToMany(
            mappedBy = "contract",
            fetch = FetchType.LAZY,
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<ContractModule> contractModules =
            new ArrayList<>();

    // =========================================================
    // DOMAIN BEHAVIOR
    // =========================================================

    public void activate(){

        assertNotTerminalState();

        if(status == ContractStatus.ACTIVE){
            return;
        }

        if(status == ContractStatus.CANCELLED){
            throw new IllegalStateException(
                    "Cannot reactivate a cancelled contract"
            );
        }

        status = ContractStatus.ACTIVE;

        activatedAt =
                Instant.now();
    }

    public void suspend(){

        assertNotTerminalState();

        if(status == ContractStatus.SUSPENDED){
            return;
        }

        status = ContractStatus.SUSPENDED;

        suspendedAt =
                Instant.now();
    }

    public void cancel(){

        if(status == ContractStatus.CANCELLED){
            return;
        }

        if(status == ContractStatus.EXPIRED){

            throw new IllegalStateException(
                    "Cannot cancel expired contract"
            );
        }

        status = ContractStatus.CANCELLED;

        cancelledAt =
                Instant.now();
    }

    public void expire(){

        if(status == ContractStatus.CANCELLED){
            return;
        }

        if(status == ContractStatus.EXPIRED){
            return;
        }

        status = ContractStatus.EXPIRED;

        setUpdatedAt(Instant.now());
    }

    public boolean isActive(){

        return status == ContractStatus.ACTIVE;
    }

    public boolean overlapsWith(
            Contract other
    ){

        return !(endDate.isBefore(other.startDate)
                ||
                startDate.isAfter(other.endDate));
    }

    private void assertNotTerminalState(){

        if(status == ContractStatus.CANCELLED){

            throw new IllegalStateException(
                    "Contract is cancelled"
            );
        }

        if(status == ContractStatus.EXPIRED){

            throw new IllegalStateException(
                    "Contract is expired"
            );
        }
    }

}