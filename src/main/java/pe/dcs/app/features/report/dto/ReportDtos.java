package pe.dcs.app.features.report.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** M20 · DTOs del catálogo de reportes, ejecución, vistas guardadas, programaciones y trabajos de exportación. */
public final class ReportDtos {

    private ReportDtos() {
    }

    public record CatalogEntry(String code, String moduleCode, String nameKey, String chartType, List<String> columns,
                               List<String> sensitiveColumns, boolean canExport) {
    }

    public record RunFilters(List<UUID> branchIds, LocalDate from, LocalDate to) {
    }

    public record RunRequest(String code, RunFilters filters) {
    }

    public record RunResult(String code, List<String> columns, List<List<Object>> rows, Map<String, Object> summary, List<String> maskedColumns) {
    }

    public record SavedReportRequest(String code, String name, RunFilters filters) {
    }

    public record SavedReportView(UUID id, String code, String name, RunFilters filters, Instant createdAt) {
    }

    public record ScheduledReportRequest(String code, RunFilters filters, String frequency, Integer dayOfWeek, Integer dayOfMonth, String format,
                                         List<UUID> recipientIds) {
    }

    public record ScheduledReportView(UUID id, String code, RunFilters filters, String frequency, Integer dayOfWeek, Integer dayOfMonth, String format,
                                      List<UUID> recipientIds, List<String> recipientNames, String status, Instant lastRunAt, Instant createdAt, long version) {
    }

    public record ExportRequest(String code, RunFilters filters, String format) {
    }

    public record ExportJobView(UUID id, String code, String format, int rowCount, String status, String errorMessage, Instant expiresAt, Instant createdAt) {
    }
}
