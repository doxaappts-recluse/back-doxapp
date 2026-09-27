package pe.dcs.app.features.report.provider;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import pe.dcs.app.features.report.spi.ReportProvider;
import pe.dcs.app.security.authz.AccessScope;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M20 · Reporte "ritos" del spec, dividido en tres (uno por cada módulo contratable de M08 — {@code BAPTISM},
 * {@code MARRIAGE}, {@code CHILD_DEDICATION} — porque {@link ReportProvider#moduleCode()} solo admite un código de
 * permiso y un solo reporte de ritos combinado ocultaría o filtraría datos entre organizaciones que contrataron
 * tipos de rito distintos; ver la bitácora de M20 (2026-09-25) para el detalle completo de por qué se dividió).
 * <p>
 * Bautizos por mes y sede, solo los ya completados (mismo criterio de "solo lo que ya ocurrió cuenta" del resto del
 * catálogo) contados por {@code event_date} (la fecha real del bautizo, no cuándo se registró en el sistema).
 */
@Component
@RequiredArgsConstructor
public class RiteBaptismReportProvider implements ReportProvider {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public String code() {
        return "RITE_BAPTISM";
    }

    @Override
    public String moduleCode() {
        return "BAPTISM";
    }

    @Override
    public String nameKey() {
        return "report.riteBaptism";
    }

    @Override
    public String chartType() {
        return "LINE";
    }

    @Override
    public List<String> columns() {
        return List.of("month", "branchName", "count");
    }

    @Override
    public ReportResult run(AccessScope scope, Filters f) {
        MapSqlParameterSource ps = new MapSqlParameterSource("org", scope.organizationId())
                .addValue("from", java.sql.Date.valueOf(f.from())).addValue("to", java.sql.Date.valueOf(f.to()));
        StringBuilder w = new StringBuilder("r.organization_id = :org and r.rite_type = 'BAPTISM' and r.status = 'COMPLETED' and r.event_date between :from and :to");
        if (!scope.allBranches()) {
            ps.addValue("branches", branchIdsOrNone(scope));
            w.append(" and r.branch_id in (:branches)");
        } else if (f.branchIds() != null && !f.branchIds().isEmpty()) {
            ps.addValue("fb", f.branchIds());
            w.append(" and r.branch_id in (:fb)");
        }
        List<Object[]> rows = jdbc.query("select to_char(date_trunc('month', r.event_date), 'YYYY-MM') as m, coalesce(b.name, '—') as bn, count(*)"
                        + " from rite r left join branch b on b.id = r.branch_id where " + w
                        + " group by 1, 2 order by 1, 2", ps, (rs, i) -> new Object[]{rs.getString(1), rs.getString(2), rs.getLong(3)});
        List<List<Object>> data = new ArrayList<>();
        long total = 0;
        for (Object[] r : rows) {
            data.add(List.of(r[0], r[1], r[2]));
            total += (Long) r[2];
        }
        return new ReportResult(columns(), data, Map.of("total", total));
    }

    private static List<UUID> branchIdsOrNone(AccessScope scope) {
        return scope.branchIds() == null || scope.branchIds().isEmpty() ? List.of(new UUID(0, 0)) : List.copyOf(scope.branchIds());
    }
}
