package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.auditable.Auditable;
import pe.dcs.app.util.enums.finance.FinancialCashRegisterStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Caja diaria de una sede: apertura con monto inicial, operación
 * durante el día (los movimientos CASH de {@link FinancialMovement}
 * son independientes, se registran igual que siempre), y cierre con
 * arqueo — se cuenta el efectivo real y se compara contra el
 * esperado (openingBalance + ingresos CASH - gastos CASH del mismo
 * día/sede, calculado en FinancialCashRegisterServiceImpl.close()).
 * A diferencia de Fondos/Presupuestos, expectedBalance y difference
 * SÍ se persisten como snapshot del momento del cierre — no tendría
 * sentido que el arqueo de una caja ya cerrada "cambiara" si se
 * cargan movimientos retroactivos después.
 *
 * Regla de negocio: solo puede haber una caja OPEN por sede a la
 * vez (ver FinancialCashRegisterRepository.existsByBranchIdAndStatus
 * y la validación en FinancialCashRegisterServiceImpl.open()).
 */
@Entity
@Table(
        name = "financial_cash_registers",
        indexes = {
                @Index(
                        name = "idx_financial_cash_register_branch",
                        columnList = "branch_id"
                ),
                @Index(
                        name = "idx_financial_cash_register_date",
                        columnList = "register_date"
                ),
                @Index(
                        name = "idx_financial_cash_register_status",
                        columnList = "status"
                )
        }
)
@Getter
@Setter
public class FinancialCashRegister extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "organization_id",
            nullable = false
    )
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "branch_id",
            nullable = false
    )
    private Branch branch;

    @Column(name = "register_date", nullable = false)
    private LocalDate registerDate;

    @Column(name = "opening_balance", nullable = false)
    private BigDecimal openingBalance;

    /** Null hasta que se cierra. */
    @Column(name = "closing_balance")
    private BigDecimal closingBalance;

    /**
     * Snapshot al momento del cierre: openingBalance + CASH
     * income - CASH expense del día/sede. Null hasta que se cierra.
     */
    @Column(name = "expected_balance")
    private BigDecimal expectedBalance;

    /** closingBalance - expectedBalance. Null hasta que se cierra. */
    @Column(name = "difference")
    private BigDecimal difference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FinancialCashRegisterStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "opened_by_user_id")
    private Person openedByUser;

    private Instant openedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_by_user_id")
    private Person closedByUser;

    private Instant closedAt;

    /**
     * Notas generales — típicamente el motivo de una diferencia
     * (faltante/sobrante) al cerrar.
     */
    @Column(length = 500)
    private String notes;
}
