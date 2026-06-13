package pe.dcs.app.features.event.response.dashboard;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NotificationsDashboard {

    private boolean noFinancialMovements;
    private boolean highCancellationRate;
}