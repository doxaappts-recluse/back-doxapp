package pe.dcs.app.features.report.spi;

import java.util.List;
import java.util.Map;

/** M20 · Igual que {@link ReportProvider} pero para reportes de plataforma (N1): sin organización, solo agregados [V1]. */
public interface PlatformReportProvider {

    String code();

    String nameKey();

    String chartType();

    List<String> columns();

    ReportProvider.ReportResult run(ReportProvider.Filters filters);

    static ReportProvider.ReportResult result(List<String> columns, List<List<Object>> rows, Map<String, Object> summary) {
        return new ReportProvider.ReportResult(columns, rows, summary);
    }
}
