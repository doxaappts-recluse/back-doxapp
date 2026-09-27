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
 * M20 · Otro de los reportes agregados tras la entrega inicial de M20 [D1]: salud de grupos por sede — grupos
 * activos (estado actual, sin filtrar por rango) y reuniones celebradas + asistencia promedio en el rango elegido
 * (misma tabla {@code attendance_record} que ya usa ATTENDANCE_TREND, vía {@code group_meeting.attendance_session_id}).
 */
@Component
@RequiredArgsConstructor
public class GroupHealthSummaryReportProvider implements ReportProvider {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public String code() {
        return "GROUP_HEALTH";
    }

    @Override
    public String moduleCode() {
        return "SMALL_GROUP";
    }

    @Override
    public String nameKey() {
        return "report.groupHealth";
    }

    @Override
    public String chartType() {
        return "BAR";
    }

    @Override
    public List<String> columns() {
        return List.of("branchName", "activeGroups", "meetingsHeld", "avgAttendance");
    }

    @Override
    public ReportResult run(AccessScope scope, Filters f) {
        MapSqlParameterSource ps = new MapSqlParameterSource("org", scope.organizationId())
                .addValue("from", java.sql.Date.valueOf(f.from())).addValue("to", java.sql.Date.valueOf(f.to()));
        StringBuilder bw = new StringBuilder("b.organization_id = :org");
        if (!scope.allBranches()) {
            ps.addValue("branches", branchIdsOrNone(scope));
            bw.append(" and b.id in (:branches)");
        } else if (f.branchIds() != null && !f.branchIds().isEmpty()) {
            ps.addValue("fb", f.branchIds());
            bw.append(" and b.id in (:fb)");
        }
        String sql = "with held as ("
                + "  select gm.branch_id as branch_id, gm.id as meeting_id, count(ar.id) filter (where ar.status = 'PRESENT') as present_ct"
                + "  from group_meeting gm left join attendance_record ar on ar.session_id = gm.attendance_session_id"
                + "  where gm.organization_id = :org and gm.status = 'HELD' and gm.meeting_date between :from and :to"
                + "  group by gm.branch_id, gm.id"
                + "), held_agg as ("
                + "  select branch_id, count(*) as meetings_held, coalesce(avg(present_ct), 0) as avg_attendance from held group by branch_id"
                + "), groups_agg as ("
                + "  select branch_id, count(*) filter (where status = 'ACTIVE') as active_groups from small_group where organization_id = :org group by branch_id"
                + ") select b.name, coalesce(ga.active_groups, 0), coalesce(ha.meetings_held, 0), round(coalesce(ha.avg_attendance, 0)::numeric, 1)"
                + " from branch b left join groups_agg ga on ga.branch_id = b.id left join held_agg ha on ha.branch_id = b.id"
                + " where " + bw + " order by b.name";
        List<Object[]> rows = jdbc.query(sql, ps, (rs, i) -> new Object[]{rs.getString(1), rs.getLong(2), rs.getLong(3), rs.getDouble(4)});
        List<List<Object>> data = new ArrayList<>();
        long totalActive = 0;
        long totalMeetings = 0;
        for (Object[] r : rows) {
            data.add(List.of(r[0], r[1], r[2], r[3]));
            totalActive += (Long) r[1];
            totalMeetings += (Long) r[2];
        }
        return new ReportResult(columns(), data, Map.of("totalActiveGroups", totalActive, "totalMeetingsHeld", totalMeetings));
    }

    private static List<UUID> branchIdsOrNone(AccessScope scope) {
        return scope.branchIds() == null || scope.branchIds().isEmpty() ? List.of(new UUID(0, 0)) : List.copyOf(scope.branchIds());
    }
}
