package pe.dcs.app.features.event.service;

import pe.dcs.app.features.event.response.reports.OccupancyReportResponse;

import java.util.List;
import java.util.UUID;

public interface OccupancyReportService {
    List<OccupancyReportResponse> get(UUID eventId);
}