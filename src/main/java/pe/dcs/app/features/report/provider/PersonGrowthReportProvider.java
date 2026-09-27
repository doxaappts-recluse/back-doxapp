package pe.dcs.app.features.report.provider;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import pe.dcs.app.features.report.spi.ReportProvider;
import pe.dcs.app.security.authz.AccessScope;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** M20 · Altas de personas por mes y sede (mismo conteo que la lista de PERSON: {@code count(*)} sobre {@code person.created_at}). */
@Component
@RequiredArgsConstructor
public class PersonGrowthReportProvider implements ReportProvider {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public String code() {
        return "PERSON_GROWTH";
    }

    @Override
    public String moduleCode() {
        return "PERSON";
    }

    @Override
    public String nameKey() {
        return "report.personGrowth";
    }

    @Override
    public String chartType() {
        return "LINE";
    }

    @Override
    public List<String> columns() {
        return List.of("month", "branchName", "newPeople");
    }

    @Override
    public ReportResult run(AccessScope scope, Filters f) {
        MapSqlParameterSource ps = new MapSqlParameterSource("org", scope.organizationId()).addValue("from", java.sql.Date.valueOf(f.from())).addValue("to", java.sql.Date.valueOf(f.to()));
        StringBuilder w = new StringBuilder("p.organization_id = :org and p.created_at::date between :from and :to");
        if (!scope.allBranches()) {
            ps.addValue("branches", branchIdsOrNone(scope));
            w.append(" and p.primary_branch_id in (:branches)");
        } else if (f.branchIds() != null && !f.branchIds().isEmpty()) {
            ps.addValue("fb", f.branchIds());
            w.append(" and p.primary_branch_id in (:fb)");
        }
        List<Object[]> rows = jdbc.query("select to_char(date_trunc('month', p.created_at), 'YYYY-MM') as m, coalesce(b.name, '—') as bn, count(*)"
                        + " from person p left join branch b on b.id = p.primary_branch_id where " + w
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
