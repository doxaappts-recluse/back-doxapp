package pe.dcs.app.features.contract.widget;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import pe.dcs.app.features.dashboard.widget.DashboardWidgetProvider;
import pe.dcs.app.features.dashboard.widget.WidgetContext;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/** M01 N1 · contratos vigentes que vencen en 7, 15 y 30 días (sin precios: soporte no ve facturación). Excluye los que ya tienen renovación. */
@Component
@RequiredArgsConstructor
public class ContractsExpiringWidget implements DashboardWidgetProvider {

    private final JdbcTemplate jdbc;

    @Override public String code() { return "CONTRACTS_EXPIRING"; }
    @Override public String moduleCode() { return "CONTRACT"; }
    @Override public String level() { return "N1"; }
    @Override public String titleKey() { return "dashboard.widgets.contractsExpiring"; }
    @Override public String size() { return "M"; }
    @Override public String kind() { return "EXPIRING"; }
    @Override public String route() { return "/platform/contracts"; }
    @Override public int order() { return 30; }

    @Override
    public Object data(WidgetContext ctx) {
        LocalDate today = ctx.now().atZone(ZoneOffset.UTC).toLocalDate();
        Date from = Date.valueOf(today);
        Date to = Date.valueOf(today.plusDays(30));
        List<Map<String, Object>> rows = jdbc.query("""
                select c.id, o.id, o.name, coalesce(c.plan_name, ''), c.end_date
                  from contract c join organization o on o.id = c.organization_id
                 where c.status = 'ACTIVE' and c.end_date between ? and ?
                   and not exists (select 1 from contract n where n.previous_contract_id = c.id and n.status in ('PENDING', 'ACTIVE'))
                 order by c.end_date, o.name
                """, (rs, i) -> {
            LocalDate end = rs.getDate(5).toLocalDate();
            return Map.<String, Object>of("contractId", rs.getObject(1).toString(), "organizationId", rs.getObject(2).toString(),
                    "organization", rs.getString(3), "plan", rs.getString(4), "endDate", end.toString(),
                    "daysLeft", ChronoUnit.DAYS.between(today, end));
        }, from, to);
        long d7 = rows.stream().filter(r -> (long) r.get("daysLeft") <= 7).count();
        long d15 = rows.stream().filter(r -> (long) r.get("daysLeft") <= 15).count();
        return Map.of("d7", d7, "d15", d15, "d30", (long) rows.size(), "rows", rows.stream().limit(8).toList());
    }
}
