package pe.dcs.app.features.contract.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.contract.ContractRenewalType;
import pe.dcs.app.util.enums.contract.LicenseDistributionMode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Edición de un contrato ya existente.
 *
 * No se puede cambiar organización, sede ni scope: eso es la
 * identidad del contrato. Si eso cambió, es otro contrato.
 * Tampoco se puede editar si está CANCELLED o EXPIRED (se
 * valida en el service).
 */
@Getter
@Setter
public class ContractUpdateRequest {

    private String planName;

    private BigDecimal price;

    private String currency;

    private LocalDate startDate;

    private LocalDate endDate;

    private Integer maxLicenses;

    private LicenseDistributionMode distributionMode;

    private ContractRenewalType renewalType;

    private List<ContractModuleRequest> modules;

    private List<ContractBranchLicenseRequest> branchLicenses;
}
