package pe.dcs.app.features.dashboard.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record DashboardResponse(String level, LocalDate from, LocalDate to, Instant generatedAt, List<WidgetResult> widgets) {
}
