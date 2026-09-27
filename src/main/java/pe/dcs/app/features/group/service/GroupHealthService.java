package pe.dcs.app.features.group.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.attendance.service.AttendanceRulesService;
import pe.dcs.app.features.group.dto.GroupDtos;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.util.Exceptions;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * M10 · Salud del grupo en un periodo de N semanas (por omisión 8): integrantes, altas y bajas, reuniones celebradas y planificadas, asistencia media y quienes llevan
 * las semanas de la regla de inasistencia (M09) sin asistir. Nivel: STALLED (sin reuniones celebradas), ATTENTION (cumplimiento &lt; 50 % o muchos ausentes), OK, NEW.
 */
@Service
@RequiredArgsConstructor
public class GroupHealthService {

    private static final int DEFAULT_WEEKS = 8;

    private final NamedParameterJdbcTemplate jdbc;
    private final GroupSupport support;
    private final AttendanceRulesService attendanceRules;

    private static int weeks(Integer w) {
        int v = w == null ? DEFAULT_WEEKS : w;
        if (v < 1 || v > 52) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "semanas");
        }
        return v;
    }

    @Transactional(readOnly = true)
    public GroupDtos.Health group(AccessScope scope, UUID id, Integer weeksParam) {
        GroupSupport.GroupRow g = support.load(scope, id);
        int w = weeks(weeksParam);
        LocalDate to = support.today(g.branchId());
        LocalDate from = to.minusWeeks(w);
        MapSqlParameterSource ps = new MapSqlParameterSource("g", id).addValue("f", java.sql.Date.valueOf(from)).addValue("t", java.sql.Date.valueOf(to));
        int members = support.activeCount(id);
        int joined = count("select count(*) from group_member where group_id = :g and joined_at between :f and :t", ps);
        int left = count("select count(*) from group_member where group_id = :g and left_at between :f and :t", ps);
        int held = count("select count(*) from group_meeting where group_id = :g and status = 'HELD' and meeting_date between :f and :t", ps);
        int planned = count("select count(*) from group_meeting where group_id = :g and status in ('HELD','PLANNED') and meeting_date between :f and :t", ps);
        Double avg = jdbc.queryForObject("select avg(a.n)::float8 from (select (select count(*) from attendance_record r where r.session_id = t.attendance_session_id"
                + " and r.status in ('PRESENT','LATE')) as n from group_meeting t where t.group_id = :g and t.status = 'HELD' and t.meeting_date between :f and :t) a", ps, Double.class);
        Double average = held == 0 || avg == null ? null : Math.round(avg * 10.0) / 10.0;
        Integer pct = planned == 0 ? null : (int) Math.round(100.0 * held / planned);
        int absentWeeks = attendanceRules.get(g.orgId()).absenceWeeksAlert();
        LocalDate absentFrom = to.minusWeeks(absentWeeks);
        MapSqlParameterSource pa = new MapSqlParameterSource("g", id).addValue("af", java.sql.Date.valueOf(absentFrom)).addValue("t", java.sql.Date.valueOf(to));
        int heldInWindow = count("select count(*) from group_meeting where group_id = :g and status = 'HELD' and meeting_date between :af and :t", pa);
        List<GroupDtos.InactiveMember> inactive = new ArrayList<>();
        if (heldInWindow > 0) {
            inactive = jdbc.query("select p.id, trim(p.first_name || ' ' || p.last_name) as name, (select max(t.meeting_date) from group_meeting t join attendance_record r on r.session_id = t.attendance_session_id"
                            + " where t.group_id = :g and t.status = 'HELD' and r.person_id = p.id and r.status in ('PRESENT','LATE')) as last_att"
                            + " from group_member m join person p on p.id = m.person_id where m.group_id = :g and m.status = 'ACTIVE' and m.joined_at <= :af"
                            + " and not exists (select 1 from group_meeting t join attendance_record r on r.session_id = t.attendance_session_id where t.group_id = :g and t.status = 'HELD'"
                            + " and t.meeting_date between :af and :t and r.person_id = p.id and r.status in ('PRESENT','LATE')) order by lower(p.last_name), lower(p.first_name) limit 50", pa,
                    (rs, i) -> new GroupDtos.InactiveMember((UUID) rs.getObject(1), rs.getString(2), rs.getDate(3) == null ? null : rs.getDate(3).toLocalDate()));
        }
        String level;
        if (planned == 0) {
            level = g.startDate() != null && g.startDate().isBefore(from) && "ACTIVE".equals(g.status()) ? "STALLED" : "NEW";
        } else if (held == 0) {
            level = "STALLED";
        } else if ((pct != null && pct < 50) || (members > 0 && inactive.size() * 3 >= members && inactive.size() >= 1)) {
            level = "ATTENTION";
        } else {
            level = "OK";
        }
        return new GroupDtos.Health(id, name(id), w, from, to, members, joined, left, joined - left, held, planned, pct, average, absentWeeks, inactive, level);
    }

    private String name(UUID id) {
        return jdbc.queryForObject("select name from small_group where id = :g", new MapSqlParameterSource("g", id), String.class);
    }

    private int count(String sql, MapSqlParameterSource ps) {
        Integer n = jdbc.queryForObject(sql, ps, Integer.class);
        return n == null ? 0 : n;
    }

    /** Comparativo por sede dentro del alcance de quien consulta (la administración de la organización ve todas). */
    @Transactional(readOnly = true)
    public GroupDtos.HealthOverview overview(AccessScope scope, Integer weeksParam) {
        int w = weeks(weeksParam);
        MapSqlParameterSource base = new MapSqlParameterSource();
        String vis = GroupSupport.visible(scope, base, "g");
        List<UUID> branches = jdbc.queryForList("select distinct g.branch_id from small_group g where " + vis, base, UUID.class);
        List<GroupDtos.BranchHealth> rows = new ArrayList<>();
        LocalDate toAll = null;
        LocalDate fromAll = null;
        for (UUID b : branches) {
            LocalDate to = support.today(b);
            LocalDate from = to.minusWeeks(w);
            toAll = toAll == null || to.isAfter(toAll) ? to : toAll;
            fromAll = fromAll == null || from.isAfter(fromAll) ? from : fromAll;
            MapSqlParameterSource ps = new MapSqlParameterSource().addValues(base.getValues());
            ps.addValue("b", b).addValue("f", java.sql.Date.valueOf(from)).addValue("t", java.sql.Date.valueOf(to));
            String where = vis + " and g.branch_id = :b";
            int active = count("select count(*) from small_group g where " + where + " and g.status = 'ACTIVE'", ps);
            int members = count("select count(*) from group_member m join small_group g on g.id = m.group_id where " + where + " and g.status = 'ACTIVE' and m.status = 'ACTIVE'", ps);
            int held = count("select count(*) from group_meeting t join small_group g on g.id = t.group_id where " + where + " and t.status = 'HELD' and t.meeting_date between :f and :t", ps);
            int planned = count("select count(*) from group_meeting t join small_group g on g.id = t.group_id where " + where + " and t.status in ('HELD','PLANNED') and t.meeting_date between :f and :t", ps);
            Double avg = jdbc.queryForObject("select avg(a.n)::float8 from (select (select count(*) from attendance_record r where r.session_id = t.attendance_session_id and r.status in ('PRESENT','LATE')) as n"
                    + " from group_meeting t join small_group g on g.id = t.group_id where " + where + " and t.status = 'HELD' and t.meeting_date between :f and :t) a", ps, Double.class);
            int joined = count("select count(*) from group_member m join small_group g on g.id = m.group_id where " + where + " and m.joined_at between :f and :t", ps);
            int left = count("select count(*) from group_member m join small_group g on g.id = m.group_id where " + where + " and m.left_at between :f and :t", ps);
            rows.add(new GroupDtos.BranchHealth(b, support.branchName(b), active, members, held, planned, held == 0 || avg == null ? null : Math.round(avg * 10.0) / 10.0, joined - left));
        }
        rows.sort((a, c) -> a.branchName().compareToIgnoreCase(c.branchName()));
        return new GroupDtos.HealthOverview(w, fromAll, toAll, rows);
    }
}
