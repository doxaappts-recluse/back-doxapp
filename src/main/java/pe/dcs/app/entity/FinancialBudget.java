package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.auditable.Auditable;
import pe.dcs.app.util.enums.StatusType;
import pe.dcs.app.util.enums.finance.FinancialMovementCategory;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Presupuesto: monto meta para una categoría/fondo/sede en un mes
 * puntual (periodYear+periodMonth) — parte del módulo "Finanzas
 * Institucionales" prometido en la propuesta comercial (ver
 * Presupuesto.docx), junto a {@link FinancialMovement} y
 * {@link FinancialFund}.
 *
 * branch/fund/category son todos opcionales e independientes entre
 * sí: sin branch es un presupuesto org-wide, sin fund no distingue
 * "bolsillo", sin category cubre todos los movimientos del scope.
 * El "gastado/ganado real" contra este presupuesto NO se persiste
 * acá — se calcula on-demand en
 * FinancialBudgetServiceImpl.progress() filtrando FinancialMovement
 * por el mismo scope+período, igual criterio que el saldo de un
 * Fondo (FinancialMovementServiceImpl.summary()).
 */
@Entity
@Table(
        name = "financial_budgets",
        indexes = {
                @Index(
                        name = "idx_financial_budget_organization",
                        columnList = "organization_id"
                ),
                @Index(
                        name = "idx_financial_budget_period",
                        columnList = "period_year, period_month"
                )
        }
)
@Getter
@Setter
public class FinancialBudget extends Auditable {

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

    /**
     * Opcional: sin definir, el presupuesto es org-wide (todas las
     * sedes). Con sede, solo compara los movimientos de esa sede.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    /**
     * Opcional: a qué fondo aplica (independiente de la categoría,
     * mismo criterio que FinancialMovement.fund).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fund_id")
    private FinancialFund fund;

    /**
     * Opcional: sin definir, cubre todas las categorías del scope
     * (ingresos + gastos mezclados). Con categoría, solo compara
     * movimientos de esa categoría puntual — el tipo (INCOME/
     * EXPENSE) queda implícito en la categoría, igual que en
     * FinancialMovement.
     */
    @Enumerated(EnumType.STRING)
    private FinancialMovementCategory category;

    @Column(name = "period_year", nullable = false)
    private Integer periodYear;

    /** 1-12. */
    @Column(name = "period_month", nullable = false)
    private Integer periodMonth;

    @Column(nullable = false)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusType status;
}
