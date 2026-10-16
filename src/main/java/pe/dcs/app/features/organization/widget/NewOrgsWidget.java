package pe.dcs.app.features.organization.widget;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import pe.dcs.app.features.dashboard.widget.DashboardWidgetProvider;
import pe.dcs.app.features.dashboard.widget.WidgetContext;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/** M01 N1 · altas de organizaciones por mes dentro del rango y las del mes en curso. */
@Component
@RequiredArgsConstructor
public class NewOrgsWidget implements DashboardWidgetProvider {

    private final JdbcTemplate jdbc;

    @Override public String code() { return "NEW_ORGS"; }
    @Override public String moduleCode() { return "ORGANIZATIONS"; }
    @Override public String level() { return "N1"; }
    @Override public String titleKey() { return "dashboard.widgets.newOrgs"; }
    @Override public String size() { return "M"; }
    @Override public String kind() { return "SERIES"; }
    @Override public String route() { return "/platform/organizations"; }
    @Override public int order() { return 20; }

    @Override
    public Object data(WidgetContext ctx) {
        List<Map<String, Object>> points = jdbc.query("""
                select to_char(m, 'YYYY-MM') as period, count(o.id) as n
                  from generate_series(date_trunc('month', ?::timestamp), date_trunc('month', ?::timestamp), interval '1 month') m
                  left join organization o on date_trunc('month', o.created_at at time zone 'UTC') = m
                 group by m order by m
                """, (rs, i) -> Map.<String, Object>of("period", rs.getString(1), "count", rs.getLong(2)),
                Date.valueOf(ctx.from()), Date.valueOf(ctx.to()));
        Timestamp monthStart = Timestamp.valueOf(ctx.now().atZone(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1).atStartOfDay());
        Long thisMonth = jdbc.queryForObject("select count(*) from organization where (created_at at time zone 'UTC') >= ?", Long.class, monthStart);
        return Map.of("thisMonth", thisMonth == null ? 0L : thisMonth, "points", points);
    }
}
