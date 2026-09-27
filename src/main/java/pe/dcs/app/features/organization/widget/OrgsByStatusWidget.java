package pe.dcs.app.features.organization.widget;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import pe.dcs.app.features.dashboard.widget.DashboardWidgetProvider;
import pe.dcs.app.features.dashboard.widget.WidgetContext;

import java.util.*;

/** M01 N1 · organizaciones por estado (solo conteos de la plataforma, V1). */
@Component
@RequiredArgsConstructor
public class OrgsByStatusWidget implements DashboardWidgetProvider {

    private static final List<String> STATUSES = List.of("DRAFT", "TRIAL", "ACTIVE", "SUSPENDED", "CLOSED");

    private final JdbcTemplate jdbc;

    @Override public String code() { return "ORGS_BY_STATUS"; }
    @Override public String moduleCode() { return "ORGANIZATIONS"; }
    @Override public String level() { return "N1"; }
    @Override public String titleKey() { return "dashboard.widgets.orgsByStatus"; }
    @Override public String size() { return "S"; }
    @Override public String kind() { return "DISTRIBUTION"; }
    @Override public String route() { return "/platform/organizations"; }
    @Override public int order() { return 10; }

    @Override
    public Object data(WidgetContext ctx) {
        Map<String, Long> counts = new HashMap<>();
        jdbc.query("select status, count(*) from organization group by status", rs -> {
            counts.put(rs.getString(1), rs.getLong(2));
        });
        List<Map<String, Object>> items = new ArrayList<>();
        long total = 0;
        for (String s : STATUSES) {
            long n = counts.getOrDefault(s, 0L);
            total += n;
            items.add(Map.of("key", s, "count", n));
        }
        return Map.of("total", total, "items", items);
    }
}
