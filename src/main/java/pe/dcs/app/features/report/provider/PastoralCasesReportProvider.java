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
 * M20 · Décimo reporte del catálogo (10 de las ~13), cubriendo "pastoral" del spec ("SLA, casos por tipo") — casos
 * por sede y tipo, abiertos en el rango elegido ({@code created_at}), con el porcentaje de los ya resueltos/cerrados
 * que cumplió su plazo ({@code due_at}, calculado por {@code PastoralRulesService} según prioridad al crear el caso).
 * Un caso sin resolver/cerrar en el rango no cuenta para el porcentaje (no se sabe todavía si cumplirá) — el
 * porcentaje es solo sobre los que ya tienen desenlace, mismo criterio de "solo lo ya definido cuenta" del resto del
 * catálogo.
 */
@Component
@RequiredArgsConstructor
public class PastoralCasesReportProvider implements ReportProvider {

    private static final String MODULE = "PASTORAL_CARE";

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public String code() {
        return "PASTORAL_CASES";
    }

    @Override
    public String moduleCode() {
        return MODULE;
    }

    @Override
    public String nameKey() {
        return "report.pastoralCases";
    }

    @Override
    public String chartType() {
        return "TABLE";
    }

    @Override
    public List<String> columns() {
        return List.of("branchName", "type", "count", "onTimePct");
    }

    @Override
    public ReportResult run(AccessScope scope, Filters f) {
        MapSqlParameterSource ps = new MapSqlParameterSource("org", scope.organizationId())
                .addValue("from", java.sql.Date.valueOf(f.from())).addValue("to", java.sql.Date.valueOf(f.to()));
        StringBuilder w = new StringBuilder("c.organization_id = :org and c.created_at::date between :from and :to");
        if (!scope.allBranches()) {
            ps.addValue("branches", branchIdsOrNone(scope));
            w.append(" and c.branch_id in (:branches)");
        } else if (f.branchIds() != null && !f.branchIds().isEmpty()) {
            ps.addValue("fb", f.branchIds());
            w.append(" and c.branch_id in (:fb)");
        }
        String sql = "select b.name, c.type, count(*),"
                + " round(100.0 * count(*) filter (where c.status in ('RESOLVED', 'CLOSED') and coalesce(c.resolved_at, c.closed_at) <= c.due_at)"
                + " / nullif(count(*) filter (where c.status in ('RESOLVED', 'CLOSED')), 0), 1)"
                + " from pastoral_case c join branch b on b.id = c.branch_id where " + w
                + " group by b.name, c.type order by b.name, c.type";
        List<Object[]> rows = jdbc.query(sql, ps, (rs, i) -> new Object[]{rs.getString(1), rs.getString(2), rs.getLong(3), rs.getObject(4)});
        List<List<Object>> data = new ArrayList<>();
        long total = 0;
        for (Object[] r : rows) {
            data.add(List.of(r[0], r[1], r[2], r[3] == null ? 0 : r[3]));
            total += (Long) r[2];
        }
        return new ReportResult(columns(), data, Map.of("total", total));
    }

    private static List<UUID> branchIdsOrNone(AccessScope scope) {
        return scope.branchIds() == null || scope.branchIds().isEmpty() ? List.of(new UUID(0, 0)) : List.copyOf(scope.branchIds());
    }
}
