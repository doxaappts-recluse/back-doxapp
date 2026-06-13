package pe.dcs.app.features.event.service;

import pe.dcs.app.features.event.response.reports.FinanceReportResponse;

import java.util.List;
import java.util.UUID;

public interface FinanceReportService {
    List<FinanceReportResponse> get(UUID eventId);
}