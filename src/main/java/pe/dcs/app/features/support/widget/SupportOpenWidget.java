package pe.dcs.app.features.support.widget;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import pe.dcs.app.features.dashboard.widget.DashboardWidgetProvider;
import pe.dcs.app.features.dashboard.widget.WidgetContext;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;

/** M01 N1 · casos de soporte en curso: por estado, sin asignar, con SLA vencido y antigüedad. */
@Component
@RequiredArgsConstructor
public class SupportOpenWidget implements DashboardWidgetProvider {

    private final JdbcTemplate jdbc;

    @Override public String code() { return "SUPPORT_OPEN"; }
    @Override public String moduleCode() { return "SUPPORT"; }
    @Override public String level() { return "N1"; }
    @Override public String titleKey() { return "dashboard.widgets.supportOpen"; }
    @Override public String size() { return "M"; }
    @Override public String kind() { return "SUPPORT"; }
    @Override public String route() { return "/platform/support"; }
    @Override public int order() { return 50; }

    @Override
    public Object data(WidgetContext ctx) {
        Timestamp now = Timestamp.from(ctx.now());
        Map<String, Object> out = new LinkedHashMap<>();
        jdbc.query("""
                select count(*) filter (where status = 'OPEN'),
                       count(*) filter (where status = 'WAITING_PLATFORM'),
                       count(*) filter (where status = 'WAITING_ORG'),
                       count(*) filter (where assignee_id is null),
                       count(*) filter (where (first_response_at is null and status in ('OPEN', 'WAITING_PLATFORM') and sla_due_at < ?)
                                           or (first_response_at is not null and first_response_at > sla_due_at)),
                       count(*) filter (where created_at >= ?::timestamptz - interval '1 day'),
                       count(*) filter (where created_at < ?::timestamptz - interval '1 day' and created_at >= ?::timestamptz - interval '3 days'),
                       count(*) filter (where created_at < ?::timestamptz - interval '3 days'),
                       coalesce(floor(extract(epoch from (?::timestamptz - min(created_at))) / 86400), 0)
                  from support_case where status in ('OPEN', 'WAITING_ORG', 'WAITING_PLATFORM')
                """, rs -> {
            out.put("open", rs.getLong(1));
            out.put("waitingPlatform", rs.getLong(2));
            out.put("waitingOrg", rs.getLong(3));
            out.put("unassigned", rs.getLong(4));
            out.put("breached", rs.getLong(5));
            out.put("ageLt1d", rs.getLong(6));
            out.put("age1to3d", rs.getLong(7));
            out.put("ageGt3d", rs.getLong(8));
            out.put("oldestDays", rs.getLong(9));
        }, now, now, now, now, now, now);
        out.put("active", (long) out.get("open") + (long) out.get("waitingPlatform") + (long) out.get("waitingOrg"));
        return out;
    }
}
