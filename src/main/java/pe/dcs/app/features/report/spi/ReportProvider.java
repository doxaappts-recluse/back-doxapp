package pe.dcs.app.features.report.spi;

import pe.dcs.app.security.authz.AccessScope;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M20 · Contrato que cada módulo implementa para aportar UN reporte al catálogo (N2/N3). "M20 solo orquesta": esta
 * interfaz es el único punto de contacto — el módulo dueño escribe su propia consulta y M20 nunca la reescribe [V9].
 */
public interface ReportProvider {

    /** Código único del reporte, p. ej. "PERSON_GROWTH". */
    String code();

    /** Módulo que lo contrata/gatea (acción V para verlo, X para exportarlo, H si expone columnas sensibles). */
    String moduleCode();

    /** Clave i18n del nombre visible. */
    String nameKey();

    /** LINE | BAR | PIE | TABLE — cómo lo dibuja el visor. */
    String chartType();

    List<String> columns();

    /** Columnas que solo se muestran con la acción H sobre {@link #moduleCode()} [V6]; vacío si no hay ninguna. */
    default List<String> sensitiveColumns() {
        return List.of();
    }

    /** Ejecuta el reporte ya acotado al alcance de sedes del actor [V3][V10]. */
    ReportResult run(AccessScope scope, Filters filters);

    record Filters(List<UUID> branchIds, LocalDate from, LocalDate to, Map<String, String> extra) {
    }

    record ReportResult(List<String> columns, List<List<Object>> rows, Map<String, Object> summary) {
    }
}
