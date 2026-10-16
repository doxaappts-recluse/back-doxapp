package pe.dcs.app.features.contract.widget;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import pe.dcs.app.features.dashboard.widget.DashboardWidgetProvider;
import pe.dcs.app.features.dashboard.widget.WidgetContext;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** M01 N1 · licencias usadas (ACTIVE + INVITED, como LicenseGuard) frente a las contratadas por organización; primero las más llenas. */
@Component
@RequiredArgsConstructor
public class LicensesUsageWidget implements DashboardWidgetProvider {

    private final JdbcTemplate jdbc;

    @Override public String code() { return "LICENSES_USAGE"; }
    @Override public String moduleCode() { return "CONTRACT"; }
    @Override public String level() { return "N1"; }
    @Override public String titleKey() { return "dashboard.widgets.licensesUsage"; }
    @Override public String size() { return "M"; }
    @Override public String kind() { return "TABLE"; }
    @Override public String route() { return "/platform/contracts"; }
    @Override public int order() { return 40; }

    @Override
    public Object data(WidgetContext ctx) {
        record Row(String id, String name, long used, long max) {
            double pct() {
                return max <= 0 ? 0 : (double) used / max;
            }
        }
        List<Row> all = jdbc.query("""
                select o.id, o.name, x.max_licenses,
                       (select count(*) from user_access ua
                         where ua.organization_id = o.id and ua.status in ('ACTIVE', 'INVITED')
                           and ua.role in ('ORG_ADMIN', 'ORG_BRANCH_ADMIN', 'ORG_USER')) as used
                  from organization o
                  join (select organization_id, sum(max_licenses) as max_licenses from contract where status = 'ACTIVE' group by organization_id) x
                    on x.organization_id = o.id
                """, (rs, i) -> new Row(rs.getObject(1).toString(), rs.getString(2), rs.getLong(4), rs.getLong(3)));
        long used = all.stream().mapToLong(Row::used).sum();
        long max = all.stream().mapToLong(Row::max).sum();
        long near = all.stream().filter(r -> r.max() > 0 && r.pct() >= 0.9).count();
        List<Map<String, Object>> rows = all.stream()
                .sorted(Comparator.comparingDouble(Row::pct).reversed().thenComparing(Row::name))
                .limit(8)
                .map(r -> Map.<String, Object>of("organizationId", r.id(), "organization", r.name(), "used", r.used(), "max", r.max(),
                        "pct", Math.round(r.pct() * 100)))
                .toList();
        return Map.of("used", used, "max", max, "nearLimit", near, "organizations", (long) all.size(), "rows", rows);
    }
}
