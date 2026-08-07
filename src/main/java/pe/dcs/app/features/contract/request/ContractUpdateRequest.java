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

    /**
     * Corrección administrativa: permite editar in-place un campo
     * "comercial" (plan/precio/moneda/licencias/módulos) de un
     * contrato ya vigente SIN pasar por el versionado de Renovación/
     * Upgrade/Downgrade — pensado para arreglar un error de tipeo,
     * no para un cambio de negocio real. Solo tiene efecto cuando NO
     * se declaró una transición de renewalType en este mismo request
     * (ver ContractServiceImpl.update(): isVersioningTransition
     * siempre tiene prioridad). No se persiste como tal; el rastro
     * de auditoría es el updatedBy/updatedAt heredado de Auditable.
     */
    private Boolean isCorrection;

    /**
     * Confirma que se quiere registrar OTRA transición del mismo tipo
     * que ya tiene el contrato (ej. un segundo Upgrade seguido, para
     * agregar más licencias el mismo día). Sin este flag,
     * ContractServiceImpl.update() no puede distinguir "el admin
     * volvió a elegir Upgrade a propósito" de "el combo simplemente
     * sigue mostrando el valor con el que ya venía el contrato" — ver
     * isVersioningTransition. Solo importa cuando renewalType es
     * igual al contract.getRenewalType() actual; si el admin elige un
     * tipo distinto (ej. estaba en RENEWAL y ahora elige UPGRADE) no
     * hace falta, ese cambio ya se detecta solo.
     */
    private Boolean sameTypeTransition;
}
