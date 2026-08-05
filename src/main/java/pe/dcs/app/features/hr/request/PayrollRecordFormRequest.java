package pe.dcs.app.features.hr.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.finance.FinancialMovementPaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class PayrollRecordFormRequest {

    @NotNull(message = "{error.empleadoObligatorio}")
    private UUID staffId;

    @NotNull(message = "{error.mesPeriodoObligatorio}")
    private Integer periodMonth;

    @NotNull(message = "{error.anioPeriodoObligatorio}")
    private Integer periodYear;

    /** Si no viene informado, se usa StaffMember.baseSalary — ver HrServiceImpl.createPayrollRecord. */
    private BigDecimal baseSalary;

    private BigDecimal bonuses;
    private BigDecimal deductions;

    private LocalDate paymentDate;
    private FinancialMovementPaymentMethod paymentMethod;
    private String notes;
}
