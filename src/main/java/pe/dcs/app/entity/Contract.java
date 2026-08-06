package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.http.HttpStatus;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.auditable.Auditable;
import pe.dcs.app.util.enums.contract.ContractRenewalType;
import pe.dcs.app.util.enums.contract.ContractScope;
import pe.dcs.app.util.enums.contract.ContractStatus;
import pe.dcs.app.util.enums.contract.LicenseDistributionMode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "contracts",
        indexes = {
                @Index(name = "idx_contract_org", columnList = "organization_id"),
                @Index(name = "idx_contract_branch", columnList = "branch_id"),
                @Index(name = "idx_contract_scope", columnList = "scope"),
                @Index(name = "idx_contract_status", columnList = "status"),
                @Index(name = "idx_contract_org_status", columnList = "organization_id,status"),
                @Index(name = "idx_contract_branch_status", columnList = "branch_id,status")
        }
)
@Getter
@Setter
public class Contract extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    // =========================================================
    // PLAN
    // =========================================================

    @Column(nullable = false)
    private String planName;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, length = 10)
    private String currency;

    // =========================================================
    // VALIDITY
    // =========================================================

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    // =========================================================
    // CAPACITY
    // =========================================================

    @Column(nullable = false)
    private Integer maxLicenses;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LicenseDistributionMode distributionMode = LicenseDistributionMode.SHARED;

    // =========================================================
    // STATUS
    // =========================================================

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContractStatus status;

    private Instant activatedAt;

    private Instant suspendedAt;

    private Instant cancelledAt;

    private Instant replacedAt;

    // =========================================================
    // LIFECYCLE
    // =========================================================

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_contract_id")
    private Contract previousContract;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContractRenewalType renewalType;

    // =========================================================
    // SCOPE
    // =========================================================

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContractScope scope;

    /**
     * Siempre pertenece a una organización.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "organization_id",
            nullable = false
    )
    private Organization organization;

    /**
     * Solo cuando el contrato aplica
     * a una sede específica.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    // =========================================================
    // MODULES
    // =========================================================

    @OneToMany(
            mappedBy = "contract",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<ContractModule> contractModules =
            new ArrayList<>();

    @OneToMany(
            mappedBy = "contract",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<ContractBranchLicense> branchLicenses =
            new ArrayList<>();

    // =========================================================
    // HELPERS
    // =========================================================

    public boolean isActive() {
        return status == ContractStatus.ACTIVE;
    }

    public boolean isOrganizationScope() {
        return scope == ContractScope.ORGANIZATION;
    }

    public boolean isBranchScope() {
        return scope == ContractScope.BRANCH;
    }

    public boolean overlapsWith(
            Contract other
    ) {

        return !(endDate.isBefore(other.startDate)
                ||
                startDate.isAfter(other.endDate));
    }

    // =========================================================
    // DOMAIN
    // =========================================================

    public void activate() {

        assertNotTerminalState();

        if (status == ContractStatus.ACTIVE) {
            return;
        }

        status = ContractStatus.ACTIVE;
        activatedAt = Instant.now();
    }

    public void suspend() {

        assertNotTerminalState();

        if (status == ContractStatus.SUSPENDED) {
            return;
        }

        status = ContractStatus.SUSPENDED;
        suspendedAt = Instant.now();
    }

    public void cancel() {

        if (status == ContractStatus.CANCELLED) {
            return;
        }

        if (status == ContractStatus.EXPIRED) {
            throw new Exceptions(
                    "error.contratoExpiradoNoCancelable",
                    HttpStatus.BAD_REQUEST
            );
        }

        status = ContractStatus.CANCELLED;
        cancelledAt = Instant.now();
    }

    /**
     * Lo llama ContractServiceImpl cuando una edición con cambio
     * comercial (plan/precio/módulos/licencias) crea un contrato
     * nuevo que reemplaza a este. No pasa por assertNotTerminalState
     * porque se invoca directamente sobre un contrato aún vigente
     * (ACTIVE/PENDING/SUSPENDED) que se está cerrando a propósito,
     * no sobre una transición de usuario tipo activate/suspend.
     */
    public void markReplaced() {
        status = ContractStatus.REPLACED;
        replacedAt = Instant.now();
    }

    public void expire() {

        if (status == ContractStatus.CANCELLED
                || status == ContractStatus.EXPIRED) {
            return;
        }

        status = ContractStatus.EXPIRED;
        setUpdatedAt(Instant.now());
    }

    // =========================================================
    // VALIDATIONS
    // =========================================================

    @PrePersist
    @PreUpdate
    private void validate() {

        validateScope();
        validateDates();
        validateCapacity();
    }

    private void validateScope() {

        if (organization == null) {
            throw new Exceptions(
                    "error.organizacionObligatoriaContrato",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (isOrganizationScope()) {

            if (branch != null) {
                throw new Exceptions(
                        "error.contratoOrganizacionNoDebeTenerSede",
                        HttpStatus.BAD_REQUEST
                );
            }

            return;
        }

        if (branch == null) {
            throw new Exceptions(
                    "error.sedeObligatoriaContrato",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (!branch.getOrganization()
                .getId()
                .equals(organization.getId())) {

            throw new Exceptions(
                    "error.sedeNoPerteneceOrganizacion",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private void validateDates() {

        if (endDate.isBefore(startDate)) {

            throw new Exceptions(
                    "error.fechaFinAnteriorFechaInicio",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private void validateCapacity() {

        if (maxLicenses <= 0) {

            throw new Exceptions(
                    "error.maximoUsuariosMayorCero",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private void assertNotTerminalState() {

        if (status == ContractStatus.CANCELLED) {

            throw new Exceptions(
                    "error.contratoCancelado",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (status == ContractStatus.EXPIRED) {

            throw new Exceptions(
                    "error.contratoExpirado",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

}