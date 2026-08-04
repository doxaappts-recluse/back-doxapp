package pe.dcs.app.features.event.response.dashboard;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AlertsDashboard {

    private boolean overBudget;

    /** Aviso temprano: gasto acumulado ya alcanzó el 80% del presupuesto, pero aún no lo supera. */
    private boolean nearBudget;

    private boolean nearCapacity;
    private boolean negativeBalance;
    private boolean noIncome;
}