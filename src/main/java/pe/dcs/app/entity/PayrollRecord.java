package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.auditable.Auditable;
import pe.dcs.app.util.enums.finance.FinancialMovementPaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Pago de planilla de un {@link StaffMember} por periodo (mes/año).
 * Delegable a org user con permiso CREATE/EDIT (ver HrAccessGuard),
 * igual que InventoryMovement. Al crearse SIEMPRE genera un
 * {@link FinancialMovement} vinculado (categoría PAYROLL, tipo
 * EXPENSE) — a diferencia de InventoryMovement, que solo lo crea
 * condicionalmente (type=IN+reason=PURCHASE+costo), acá el pago de
 * planilla siempre implica una salida de caja real (ver
 * HrServiceImpl.syncFinancialMovement, mismo criterio incondicional
 * que MarriageServiceImpl con la tarifa de matrimonio).
 *
 * Inmutable una vez creado — no update/delete, mismo criterio que
 * InventoryMovement (registro de planilla ya pagada; una corrección
 * se maneja fuera del sistema o con un ajuste manual en Finanzas).
 */
@Entity
@Table(
        name = "hr_payroll_records",
        indexes = {
                @Index(name = "idx_payroll_record_staff", columnList = "staff_id"),
                @Index(name = "idx_payroll_record_period", columnList = "period_year, period_month")
        }
)
@Getter
@Setter
public class PayrollRecord extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id", nullable = false)
    private StaffMember staff;

    @Column(name = "period_month", nullable = false)
    private Integer periodMonth;

    @Column(name = "period_year", nullable = false)
    private Integer periodYear;

    @Column(name = "base_salary", nullable = false, precision = 12, scale = 2)
    private BigDecimal baseSalary;

    @Column(precision = 12, scale = 2)
    private BigDecimal bonuses;

    @Column(precision = 12, scale = 2)
    private BigDecimal deductions;

    @Column(name = "net_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal netAmount;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Enumerated(EnumType.STRING)
    private FinancialMovementPaymentMethod paymentMethod;

    @Column(length = 1000)
    private String notes;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "financial_movement_id")
    private FinancialMovement financialMovement;
}
