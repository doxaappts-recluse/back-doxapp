package pe.dcs.app.features.hr.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.StatusType;
import pe.dcs.app.util.enums.hr.HrContractType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class StaffMemberFormRequest {

    /** Solo requerido al crear — ver HrServiceImpl.createStaff. */
    private UUID personId;

    @NotBlank(message = "{error.cargoObligatorio}")
    private String position;

    @NotNull(message = "{error.tipoContratoObligatorio}")
    private HrContractType contractType;

    private BigDecimal baseSalary;

    @NotNull(message = "{error.fechaIngresoObligatoria}")
    private LocalDate hireDate;

    private LocalDate terminationDate;
    private String notes;

    /** Solo relevante para org admin (elige sede libremente); igual criterio que el resto de features. */
    private UUID branchId;

    private StatusType status;
}
