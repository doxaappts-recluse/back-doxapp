package pe.dcs.app.features.contract.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

    @NotBlank(message = "{error.elPlanEsObligatorio}")
    private String planName;

    @NotNull(message = "{error.precioContratoObligatorio}")
    private BigDecimal price;

    @NotBlank(message = "{error.monedaContratoObligatoria}")
    private String currency;

    @NotNull(message = "{error.fechaInicioObligatoria}")
    private LocalDate startDate;

    private LocalDate endDate;

    @NotNull(message = "{error.licenciasMaximasObligatorias}")
    private Integer maxLicenses;

    private LicenseDistributionMode distributionMode;

    private ContractRenewalType renewalType;

    private List<ContractModuleRequest> modules;

    private List<ContractBranchLicenseRequest> branchLicenses;
}
