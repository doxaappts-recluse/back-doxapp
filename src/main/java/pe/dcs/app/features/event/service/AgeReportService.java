package pe.dcs.app.features.event.service;

import pe.dcs.app.features.event.response.reports.AgeReportResponse;

import java.util.List;
import java.util.UUID;

public interface AgeReportService {

    List<AgeReportResponse> get(UUID eventId);

}