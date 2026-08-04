package pe.dcs.app.features.hr.request;

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

    private String position;
    private HrContractType contractType;
    private BigDecimal baseSalary;
    private LocalDate hireDate;
    private LocalDate terminationDate;
    private String notes;

    /** Solo relevante para org admin (elige sede libremente); igual criterio que el resto de features. */
    private UUID branchId;

    private StatusType status;
}
