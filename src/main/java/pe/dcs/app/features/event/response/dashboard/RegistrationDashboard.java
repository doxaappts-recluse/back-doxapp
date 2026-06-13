package pe.dcs.app.features.event.response.dashboard;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegistrationDashboard {

    private long totalRegistrations;
    private long totalCancelled;
    private long totalActive;
    private double occupancyRate;
}