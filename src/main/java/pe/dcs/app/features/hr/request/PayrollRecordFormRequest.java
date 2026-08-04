package pe.dcs.app.features.hr.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.finance.FinancialMovementPaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class PayrollRecordFormRequest {

    private UUID staffId;

    private Integer periodMonth;
    private Integer periodYear;

    /** Si no viene informado, se usa StaffMember.baseSalary — ver HrServiceImpl.createPayrollRecord. */
    private BigDecimal baseSalary;

    private BigDecimal bonuses;
    private BigDecimal deductions;

    private LocalDate paymentDate;
    private FinancialMovementPaymentMethod paymentMethod;
    private String notes;
}
