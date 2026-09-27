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
 * M20 · Uno de los reportes agregados tras la entrega inicial de M20 [D1]: embudo de visitantes por sede y etapa
 * en el rango elegido (mismas etapas y misma columna {@code first_visit_date} que usa el módulo VISITOR).
 */
@Component
@RequiredArgsConstructor
public class VisitorFunnelReportProvider implements ReportProvider {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public String code() {
        return "VISITOR_FUNNEL";
    }

    @Override
    public String moduleCode() {
        return "VISITOR";
    }

    @Override
    public String nameKey() {
        return "report.visitorFunnel";
    }

    @Override
    public String chartType() {
        return "BAR";
    }

    @Override
    public List<String> columns() {
        return List.of("branchName", "stage", "count");
    }

    @Override
    public ReportResult run(AccessScope scope, Filters f) {
        MapSqlParameterSource ps = new MapSqlParameterSource("org", scope.organizationId())
                .addValue("from", java.sql.Date.valueOf(f.from())).addValue("to", java.sql.Date.valueOf(f.to()));
        StringBuilder w = new StringBuilder("vc.organization_id = :org and vc.first_visit_date between :from and :to");
        if (!scope.allBranches()) {
            ps.addValue("branches", branchIdsOrNone(scope));
            w.append(" and vc.branch_id in (:branches)");
        } else if (f.branchIds() != null && !f.branchIds().isEmpty()) {
            ps.addValue("fb", f.branchIds());
            w.append(" and vc.branch_id in (:fb)");
        }
        List<Object[]> rows = jdbc.query("select coalesce(b.name, '—') as bn, vc.stage, count(*)"
                        + " from visitor_case vc join branch b on b.id = vc.branch_id where " + w
                        + " group by 1, 2 order by 1, 2", ps, (rs, i) -> new Object[]{rs.getString(1), rs.getString(2), rs.getLong(3)});
        List<List<Object>> data = new ArrayList<>();
        long total = 0;
        long integrated = 0;
        for (Object[] r : rows) {
            data.add(List.of(r[0], r[1], r[2]));
            total += (Long) r[2];
            String stage = (String) r[1];
            if ("INTEGRATED".equals(stage) || "CONVERTED".equals(stage)) {
                integrated += (Long) r[2];
            }
        }
        return new ReportResult(columns(), data, Map.of("total", total, "integrated", integrated));
    }

    private static List<UUID> branchIdsOrNone(AccessScope scope) {
        return scope.branchIds() == null || scope.branchIds().isEmpty() ? List.of(new UUID(0, 0)) : List.copyOf(scope.branchIds());
    }
}
