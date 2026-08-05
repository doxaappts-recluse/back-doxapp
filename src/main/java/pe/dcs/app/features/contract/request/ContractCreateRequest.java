package pe.dcs.app.features.contract.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

    @NotNull(message = "{error.laOrganizacionEsObligatoria}")
    private UUID organizationId;

    @NotNull(message = "{error.debeIndicarAlcanceContratoOrganizacionSede}")
    private ContractScope scope;

    /**
     * Solo cuando scope = BRANCH.
     */
    private UUID branchId;

    // =========================================================
    // PLAN
    // =========================================================

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
