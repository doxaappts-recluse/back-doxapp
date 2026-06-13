package pe.dcs.app.features.event.service;

import pe.dcs.app.features.event.response.reports.RegistrationReportResponse;

import java.util.List;
import java.util.UUID;

public interface RegistrationReportService {
    List<RegistrationReportResponse> get(UUID eventId);
}