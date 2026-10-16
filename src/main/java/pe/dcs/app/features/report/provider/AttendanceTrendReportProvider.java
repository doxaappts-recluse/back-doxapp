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

/** M20 · Tendencia de asistencia por mes y sede (misma tabla y estado 'PRESENT' que usa el módulo ATTENDANCE). */
@Component
@RequiredArgsConstructor
public class AttendanceTrendReportProvider implements ReportProvider {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public String code() {
        return "ATTENDANCE_TREND";
    }

    @Override
    public String moduleCode() {
        return "ATTENDANCE";
    }

    @Override
    public String nameKey() {
        return "report.attendanceTrend";
    }

    @Override
    public String chartType() {
        return "LINE";
    }

    @Override
    public List<String> columns() {
        return List.of("month", "branchName", "sessions", "present");
    }

    @Override
    public ReportResult run(AccessScope scope, Filters f) {
        MapSqlParameterSource ps = new MapSqlParameterSource("org", scope.organizationId())
                .addValue("from", java.sql.Date.valueOf(f.from())).addValue("to", java.sql.Date.valueOf(f.to()));
        StringBuilder w = new StringBuilder("s.organization_id = :org and s.session_date between :from and :to and s.status = 'CLOSED'");
        if (!scope.allBranches()) {
            ps.addValue("branches", branchIdsOrNone(scope));
            w.append(" and s.branch_id in (:branches)");
        } else if (f.branchIds() != null && !f.branchIds().isEmpty()) {
            ps.addValue("fb", f.branchIds());
            w.append(" and s.branch_id in (:fb)");
        }
        List<Object[]> rows = jdbc.query("select to_char(date_trunc('month', s.session_date), 'YYYY-MM') as m, coalesce(b.name, '—') as bn,"
                        + " count(distinct s.id), count(ar.id) filter (where ar.status = 'PRESENT')"
                        + " from attendance_session s left join branch b on b.id = s.branch_id left join attendance_record ar on ar.session_id = s.id"
                        + " where " + w + " group by 1, 2 order by 1, 2", ps, (rs, i) -> new Object[]{rs.getString(1), rs.getString(2), rs.getLong(3), rs.getLong(4)});
        List<List<Object>> data = new ArrayList<>();
        long totalPresent = 0;
        for (Object[] r : rows) {
            data.add(List.of(r[0], r[1], r[2], r[3]));
            totalPresent += (Long) r[3];
        }
        return new ReportResult(columns(), data, Map.of("totalPresent", totalPresent));
    }

    private static List<UUID> branchIdsOrNone(AccessScope scope) {
        return scope.branchIds() == null || scope.branchIds().isEmpty() ? List.of(new UUID(0, 0)) : List.copyOf(scope.branchIds());
    }
}
