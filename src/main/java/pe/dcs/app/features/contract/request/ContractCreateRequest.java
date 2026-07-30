package pe.dcs.app.features.contract.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.contract.ContractRenewalType;
import pe.dcs.app.util.enums.contract.ContractScope;
import pe.dcs.app.util.enums.contract.LicenseDistributionMode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class ContractCreateRequest {

    // =========================================================
    // SCOPE
    // =========================================================

    private UUID organizationId;

    private ContractScope scope;

    /**
     * Solo cuando scope = BRANCH.
     */
    private UUID branchId;

    // =========================================================
    // PLAN
    // =========================================================

    private String planName;

    private BigDecimal price;

    private String currency;

    private LocalDate startDate;

    private LocalDate endDate;

    private Integer maxLicenses;

    /**
     * Solo tiene sentido cuando scope = ORGANIZATION.
     * Default SHARED si no se envía.
     */
    private LicenseDistributionMode distributionMode;

    private ContractRenewalType renewalType;

    /**
     * Opcional: contrato al que reemplaza (renovación/upgrade/downgrade).
     */
    private UUID previousContractId;

    // =========================================================
    // MODULOS / PERMISOS
    // =========================================================

    private List<ContractModuleRequest> modules;

    // =========================================================
    // REPARTO DE LICENCIAS (solo ORGANIZATION + ALLOCATED)
    // =========================================================

    private List<ContractBranchLicenseRequest> branchLicenses;
}
