package pe.dcs.app.features.event.response.dashboard;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AlertsDashboard {

    private boolean overBudget;
    private boolean nearCapacity;
    private boolean negativeBalance;
    private boolean noIncome;
}