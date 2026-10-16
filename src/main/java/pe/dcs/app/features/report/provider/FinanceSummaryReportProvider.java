package pe.dcs.app.features.report.provider;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import pe.dcs.app.features.report.spi.ReportProvider;
import pe.dcs.app.security.authz.AccessScope;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** M20 · Ingresos y egresos por fondo (solo movimientos APPROVED — la misma condición que el estado de cuenta de FIN_MOVEMENTS). */
@Component
@RequiredArgsConstructor
public class FinanceSummaryReportProvider implements ReportProvider {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public String code() {
        return "FINANCE_SUMMARY";
    }

    @Override
    public String moduleCode() {
        return "FIN_MOVEMENTS";
    }

    @Override
    public String nameKey() {
        return "report.financeSummary";
    }

    @Override
    public String chartType() {
        return "BAR";
    }

    @Override
    public List<String> columns() {
        return List.of("fundName", "type", "total");
    }

    @Override
    public ReportResult run(AccessScope scope, Filters f) {
        MapSqlParameterSource ps = new MapSqlParameterSource("org", scope.organizationId())
                .addValue("from", java.sql.Date.valueOf(f.from())).addValue("to", java.sql.Date.valueOf(f.to()));
        StringBuilder w = new StringBuilder("m.organization_id = :org and m.movement_date between :from and :to and m.status = 'APPROVED'");
        if (!scope.allBranches()) {
            ps.addValue("branches", branchIdsOrNone(scope));
            w.append(" and m.branch_id in (:branches)");
        } else if (f.branchIds() != null && !f.branchIds().isEmpty()) {
            ps.addValue("fb", f.branchIds());
            w.append(" and m.branch_id in (:fb)");
        }
        List<Object[]> rows = jdbc.query("select fnd.name, m.type, sum(m.amount) from fin_movement m join fin_fund fnd on fnd.id = m.fund_id where " + w
                + " group by fnd.name, m.type order by fnd.name, m.type", ps, (rs, i) -> new Object[]{rs.getString(1), rs.getString(2), rs.getBigDecimal(3)});
        List<List<Object>> data = new ArrayList<>();
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        for (Object[] r : rows) {
            data.add(List.of(r[0], r[1], r[2]));
            if ("INCOME".equals(r[1])) {
                income = income.add((BigDecimal) r[2]);
            } else {
                expense = expense.add((BigDecimal) r[2]);
            }
        }
        return new ReportResult(columns(), data, Map.of("income", income, "expense", expense, "balance", income.subtract(expense)));
    }

    private static List<UUID> branchIdsOrNone(AccessScope scope) {
        return scope.branchIds() == null || scope.branchIds().isEmpty() ? List.of(new UUID(0, 0)) : List.copyOf(scope.branchIds());
    }
}
