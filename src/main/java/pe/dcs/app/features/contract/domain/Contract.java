package pe.dcs.app.features.contract.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.shared.domain.TenantScoped;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * M03 · Contrato. Un contrato ACTIVE habilita los módulos contratables de la organización y fija el cupo de licencias.
 * Los cambios comerciales se versionan: el contrato anterior queda REPLACED y el nuevo apunta a él con {@code previousContractId}.
 */
@Getter
@Setter
@Entity
@Table(name = "contract")
public class Contract extends TenantScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "plan_id")
    private UUID planId;

    /** Copia del nombre del plan al momento de contratar (el plan puede cambiar o retirarse después). */
    @Column(name = "plan_name", length = 100)
    private String planName;

    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "PEN";

    @Column(name = "branch_id")
    private UUID branchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ContractStatus status = ContractStatus.PENDING;

    @Column(name = "status_reason", length = 255)
    private String statusReason;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "max_licenses", nullable = false)
    private int maxLicenses;

    /** null = sin límite de sedes. */
    @Column(name = "max_branches")
    private Integer maxBranches;

    @Enumerated(EnumType.STRING)
    @Column(name = "distribution_mode", nullable = false, length = 20)
    private LicenseDistributionMode distributionMode = LicenseDistributionMode.SHARED;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 20)
    private ContractScope scope = ContractScope.ORGANIZATION;

    @Enumerated(EnumType.STRING)
    @Column(name = "renewal_type", nullable = false, length = 20)
    private RenewalType renewalType = RenewalType.NEW;

    @Column(name = "previous_contract_id")
    private UUID previousContractId;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "suspended_at")
    private Instant suspendedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "replaced_at")
    private Instant replacedAt;

    @Column(name = "expired_at")
    private Instant expiredAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "contract_module", joinColumns = @JoinColumn(name = "contract_id"))
    @Column(name = "module_code", length = 40)
    private Set<String> moduleCodes = new LinkedHashSet<>();

    public boolean isTerminal() {
        return status == ContractStatus.EXPIRED || status == ContractStatus.CANCELLED || status == ContractStatus.REPLACED;
    }
}
