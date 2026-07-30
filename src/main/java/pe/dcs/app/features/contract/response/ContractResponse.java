package pe.dcs.app.features.contract.response;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.contract.ContractRenewalType;
import pe.dcs.app.util.enums.contract.ContractScope;
import pe.dcs.app.util.enums.contract.ContractStatus;
import pe.dcs.app.util.enums.contract.LicenseDistributionMode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Detalle completo de un contrato: para el formulario de
 * edición (catálogo de módulos ya marcado con "assigned").
 */
@Getter
@Setter
public class ContractResponse {

    private UUID id;

    private ContractScope scope;

    private UUID organizationId;

    private String organizationName;

    private UUID branchId;

    private String branchName;

    private String planName;

    private BigDecimal price;

    private String currency;

    private LocalDate startDate;

    private LocalDate endDate;

    private Integer maxLicenses;

    private LicenseDistributionMode distributionMode;

    private ContractStatus status;

    private ContractRenewalType renewalType;

    private UUID previousContractId;

    private List<ContractModuleConfigResponse> modules;

    private List<ContractBranchLicenseResponse> branchLicenses;
}
