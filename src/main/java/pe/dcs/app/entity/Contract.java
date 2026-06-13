package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.enums.contract.ContractRenewalType;
import pe.dcs.app.util.enums.contract.ContractStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "contracts",
        indexes = {
                @Index(name = "idx_contract_org", columnList = "organization_id"),
                @Index(name = "idx_contract_status", columnList = "status"),
                @Index(name = "idx_contract_org_start_date", columnList = "organization_id, start_date")
        }
)
@Getter
@Setter
public class Contract {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

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
    // ESTADO (SOURCE OF TRUTH)
    // =========================
    @Enumerated(EnumType.STRING)
    private ContractStatus status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private LocalDateTime suspendedAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime activatedAt;

    // =========================
    // CICLO DE VIDA
    // =========================
    private UUID previousContractId;

    @Enumerated(EnumType.STRING)
    private ContractRenewalType renewalType;

    @OneToMany(mappedBy = "contract", fetch = FetchType.LAZY)
    private List<ContractModule> contractModules;

    // =========================================================
    // DOMAIN BEHAVIOR
    // =========================================================

    public void activate() {
        assertNotTerminalState();

        if (this.status == ContractStatus.ACTIVE) {
            return;
        }

        if (this.status == ContractStatus.CANCELLED) {
            throw new IllegalStateException("Cannot reactivate a cancelled contract");
        }

        this.status = ContractStatus.ACTIVE;
        this.activatedAt = LocalDateTime.now();
    }

    public void suspend() {

        assertNotTerminalState();

        if (this.status == ContractStatus.SUSPENDED) {
            return;
        }

        this.status = ContractStatus.SUSPENDED;
        this.suspendedAt = LocalDateTime.now();
    }

    public void cancel() {
        if (this.status == ContractStatus.CANCELLED) {
            return;
        }

        if (this.status == ContractStatus.EXPIRED) {
            throw new IllegalStateException("Cannot cancel an expired contract");
        }

        this.status = ContractStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
    }

    public void expireManually() {
        this.status = ContractStatus.EXPIRED;
        this.updatedAt = LocalDateTime.now();
    }

    public void activateManually() {

        if (this.status == ContractStatus.CANCELLED) {
            throw new IllegalStateException("Cancelled contract cannot be activated");
        }

        if (this.status == ContractStatus.EXPIRED) {
            throw new IllegalStateException("Expired contract cannot be activated");
        }

        if (this.status != ContractStatus.PENDING) {
            throw new IllegalStateException("Only pending contracts can be activated");
        }

        if (LocalDate.now().isBefore(this.startDate)) {
            throw new IllegalStateException(
                    "Contract cannot be activated before start date"
            );
        }

        this.status = ContractStatus.ACTIVE;
        this.activatedAt = LocalDateTime.now();
    }

    // =========================================================
    // DERIVED RULES
    // =========================================================

    private void assertNotTerminalState() {
        if (this.status == ContractStatus.CANCELLED) {
            throw new IllegalStateException("Contract is cancelled and cannot be modified");
        }

        if (this.status == ContractStatus.EXPIRED) {
            throw new IllegalStateException("Contract is expired and cannot be modified");
        }
    }

    public boolean isActive() {
        return this.status == ContractStatus.ACTIVE;
    }

    public boolean overlapsWith(Contract other) {
        return !(this.endDate.isBefore(other.startDate)
                || this.startDate.isAfter(other.endDate));
    }

    public void expire() {

        if (this.status == ContractStatus.CANCELLED) {
            return;
        }

        if (this.status == ContractStatus.EXPIRED) {
            return;
        }

        this.status = ContractStatus.EXPIRED;
    }

}