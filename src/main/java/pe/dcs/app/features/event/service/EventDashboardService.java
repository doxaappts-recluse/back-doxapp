package pe.dcs.app.features.event.service;

import pe.dcs.app.features.event.response.dashboard.EventDashboardResponse;

import java.util.UUID;

public interface EventDashboardService {
    EventDashboardResponse getDashboard(UUID eventId);
}