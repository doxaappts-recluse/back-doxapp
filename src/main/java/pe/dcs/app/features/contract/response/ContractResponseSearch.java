package pe.dcs.app.features.contract.response;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.auditable.AuditableResponse;
import pe.dcs.app.util.enums.contract.ContractRenewalType;
import pe.dcs.app.util.enums.contract.ContractScope;
import pe.dcs.app.util.enums.contract.ContractStatus;
import pe.dcs.app.util.enums.contract.LicenseDistributionMode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Fila para el listado/historial. Sin módulos: eso solo
 * se pide en el detalle (getById).
 */
@Getter
@Setter
public class ContractResponseSearch extends AuditableResponse {

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
}
