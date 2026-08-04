package pe.dcs.app.features.finance.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Totales agregados de movimientos financieros para un filtro dado
 * (saldo de un fondo, total aportado por un donante, etc.). Solo
 * considera movimientos APROBADOS — un PENDING no es plata real
 * todavía y un REJECTED nunca ocurrió, así que ninguno de los dos
 * debe alterar un saldo (ver FinancialMovementServiceImpl.summary).
 */
@Getter
@Setter
public class FinancialMovementSummaryResponse {

    private BigDecimal totalIncome;

    private BigDecimal totalExpense;

    private BigDecimal balance;

    private long movementCount;
}
