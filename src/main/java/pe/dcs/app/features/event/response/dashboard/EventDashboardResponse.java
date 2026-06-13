package pe.dcs.app.features.event.response.dashboard;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EventDashboardResponse {

    // =========================
    // FINANCE
    // =========================
    private FinanceDashboard finance;

    // =========================
    // REGISTRATION
    // =========================
    private RegistrationDashboard registration;

    // =========================
    // INSIGHTS
    // =========================
    private AlertsDashboard alerts;

    private NotificationsDashboard notifications;
}