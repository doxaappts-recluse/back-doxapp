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

/** M20 · Resumen de planilla por periodo y sede; los montos son sensibles [V6] — el catálogo los oculta sin la acción H sobre HR_PAYROLL. */
@Component
@RequiredArgsConstructor
public class HrPayrollSummaryReportProvider implements ReportProvider {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public String code() {
        return "HR_PAYROLL_SUMMARY";
    }

    @Override
    public String moduleCode() {
        return "HR_PAYROLL";
    }

    @Override
    public String nameKey() {
        return "report.hrPayrollSummary";
    }

    @Override
    public String chartType() {
        return "TABLE";
    }

    @Override
    public List<String> columns() {
        return List.of("period", "branchName", "staffCount", "grossTotal", "netTotal");
    }

    @Override
    public List<String> sensitiveColumns() {
        return List.of("grossTotal", "netTotal");
    }

    @Override
    public ReportResult run(AccessScope scope, Filters f) {
        MapSqlParameterSource ps = new MapSqlParameterSource("org", scope.organizationId())
                .addValue("from", f.from().toString().substring(0, 7)).addValue("to", f.to().toString().substring(0, 7));
        StringBuilder w = new StringBuilder("r.organization_id = :org and r.period between :from and :to and r.status in ('APPROVED', 'PAID', 'CLOSED')");
        if (!scope.allBranches()) {
            ps.addValue("branches", branchIdsOrNone(scope));
            w.append(" and coalesce(r.branch_id, '00000000-0000-0000-0000-000000000000'::uuid) in (:branches)");
        } else if (f.branchIds() != null && !f.branchIds().isEmpty()) {
            ps.addValue("fb", f.branchIds());
            w.append(" and r.branch_id in (:fb)");
        }
        List<Object[]> rows = jdbc.query("select r.period, coalesce(b.name, 'Toda la organización'), count(pr.id), sum(pr.gross), sum(pr.net)"
                        + " from payroll_run r left join branch b on b.id = r.branch_id join payroll_record pr on pr.run_id = r.id where " + w
                        + " group by r.period, b.name order by r.period", ps,
                (rs, i) -> new Object[]{rs.getString(1), rs.getString(2), rs.getLong(3), rs.getBigDecimal(4), rs.getBigDecimal(5)});
        List<List<Object>> data = new ArrayList<>();
        for (Object[] r : rows) {
            data.add(List.of(r[0], r[1], r[2], r[3], r[4]));
        }
        return new ReportResult(columns(), data, Map.of("periods", data.size()));
    }

    private static List<UUID> branchIdsOrNone(AccessScope scope) {
        return scope.branchIds() == null || scope.branchIds().isEmpty() ? List.of(new UUID(0, 0)) : List.copyOf(scope.branchIds());
    }
}
