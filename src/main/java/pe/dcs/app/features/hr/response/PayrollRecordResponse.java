package pe.dcs.app.features.hr.response;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.auditable.AuditableResponse;
import pe.dcs.app.util.enums.finance.FinancialMovementPaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class PayrollRecordResponse extends AuditableResponse {

    private UUID id;

    private UUID staffId;
    private String staffName;

    private UUID branchId;
    private String branchName;

    private Integer periodMonth;
    private Integer periodYear;

    private BigDecimal baseSalary;
    private BigDecimal bonuses;
    private BigDecimal deductions;
    private BigDecimal netAmount;

    private LocalDate paymentDate;
    private FinancialMovementPaymentMethod paymentMethod;
    private String notes;

    /** Siempre informado — a diferencia de Inventory, el pago de planilla siempre genera un FinancialMovement. */
    private UUID financialMovementId;

    private boolean canManage;
}
