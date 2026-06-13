package pe.dcs.app.features.event.response.dashboard;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class FinanceDashboard {

    private BigDecimal totalIncome;
    private BigDecimal totalExpense;
    private BigDecimal balance;
    private BigDecimal pendingIncome;
    private BigDecimal pendingExpense;
}