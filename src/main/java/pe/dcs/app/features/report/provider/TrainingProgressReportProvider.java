package pe.dcs.app.features.report.provider;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import pe.dcs.app.features.report.spi.ReportProvider;
import pe.dcs.app.features.training.service.TrainingSupport;
import pe.dcs.app.security.authz.AccessScope;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M20 · Onceavo reporte del catálogo (11 de las ~13), cubriendo "formación" del spec ("avance, aprobación por
 * curso") — matrículas y tasa de aprobación por curso y sede, sobre las matrículas creadas en el rango elegido
 * ({@code enrolled_at}). La tasa de aprobación solo se calcula sobre matrículas ya resueltas ({@code APPROVED} o
 * {@code FAILED}); las que siguen {@code ENROLLED} (en curso) o {@code WITHDRAWN} (retiradas) no cuentan para el
 * porcentaje, mismo criterio de "solo lo ya definido cuenta" que PASTORAL_CASES.
 */
@Component
@RequiredArgsConstructor
public class TrainingProgressReportProvider implements ReportProvider {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public String code() {
        return "TRAINING_PROGRESS";
    }

    @Override
    public String moduleCode() {
        return TrainingSupport.MODULE;
    }

    @Override
    public String nameKey() {
        return "report.trainingProgress";
    }

    @Override
    public String chartType() {
        return "TABLE";
    }

    @Override
    public List<String> columns() {
        return List.of("branchName", "courseName", "enrolled", "approved", "approvalPct");
    }

    @Override
    public ReportResult run(AccessScope scope, Filters f) {
        MapSqlParameterSource ps = new MapSqlParameterSource("org", scope.organizationId())
                .addValue("from", java.sql.Date.valueOf(f.from())).addValue("to", java.sql.Date.valueOf(f.to()));
        StringBuilder w = new StringBuilder("e.organization_id = :org and e.enrolled_at::date between :from and :to");
        if (!scope.allBranches()) {
            ps.addValue("branches", branchIdsOrNone(scope));
            w.append(" and e.branch_id in (:branches)");
        } else if (f.branchIds() != null && !f.branchIds().isEmpty()) {
            ps.addValue("fb", f.branchIds());
            w.append(" and e.branch_id in (:fb)");
        }
        String sql = "select b.name, co.name, count(*),"
                + " count(*) filter (where e.status = 'APPROVED'),"
                + " round(100.0 * count(*) filter (where e.status = 'APPROVED') / nullif(count(*) filter (where e.status in ('APPROVED', 'FAILED')), 0), 1)"
                + " from enrollment e join course_class cc on cc.id = e.class_id join course co on co.id = cc.course_id join branch b on b.id = e.branch_id"
                + " where " + w + " group by b.name, co.name order by b.name, co.name";
        List<Object[]> rows = jdbc.query(sql, ps, (rs, i) -> new Object[]{rs.getString(1), rs.getString(2), rs.getLong(3), rs.getLong(4), rs.getObject(5)});
        List<List<Object>> data = new ArrayList<>();
        long totalEnrolled = 0;
        long totalApproved = 0;
        for (Object[] r : rows) {
            data.add(List.of(r[0], r[1], r[2], r[3], r[4] == null ? 0 : r[4]));
            totalEnrolled += (Long) r[2];
            totalApproved += (Long) r[3];
        }
        return new ReportResult(columns(), data, Map.of("totalEnrolled", totalEnrolled, "totalApproved", totalApproved));
    }

    private static List<UUID> branchIdsOrNone(AccessScope scope) {
        return scope.branchIds() == null || scope.branchIds().isEmpty() ? List.of(new UUID(0, 0)) : List.copyOf(scope.branchIds());
    }
}
