package pe.dcs.app.features.report.provider;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import pe.dcs.app.features.report.spi.ReportProvider;
import pe.dcs.app.features.volunteer.service.VolunteerSupport;
import pe.dcs.app.security.authz.AccessScope;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M20 · Noveno reporte del catálogo (9 de las ~13), cubriendo "voluntariado" del spec ("cobertura, horas, screening
 * por vencer") — se implementa cobertura + horas servidas, mismo criterio del resto del catálogo de tomar un ángulo
 * representativo por categoría; "screening por vencer" queda fuera (es una alerta operativa puntual, no una serie
 * agregable en un rango de fechas como el resto de este catálogo).
 * <p>
 * Cobertura = cupos necesarios ({@code shift_slot.needed}, sumado por sede sobre los planes de servicio del rango)
 * vs. cupos cubiertos (asignaciones {@code CONFIRMED} o {@code SERVED}, que representan compromiso real, no solo
 * propuestas {@code PROPOSED}). Horas servidas solo cuenta asignaciones {@code SERVED} (turno efectivamente cumplido),
 * no las simplemente confirmadas — mismo criterio de "solo lo que ya ocurrió cuenta" que usa FINANCE_SUMMARY con
 * movimientos {@code APPROVED}.
 */
@Component
@RequiredArgsConstructor
public class VolunteerCoverageReportProvider implements ReportProvider {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public String code() {
        return "VOLUNTEER_COVERAGE";
    }

    @Override
    public String moduleCode() {
        return VolunteerSupport.MODULE;
    }

    @Override
    public String nameKey() {
        return "report.volunteerCoverage";
    }

    @Override
    public String chartType() {
        return "BAR";
    }

    @Override
    public List<String> columns() {
        return List.of("branchName", "slotsNeeded", "slotsFilled", "hoursServed");
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
        String sql = "with slots as ("
                + "  select sp.branch_id, ss.id as slot_id, ss.needed, ss.start_time, ss.end_time"
                + "  from shift_slot ss join service_plan sp on sp.id = ss.plan_id"
                + "  where sp.organization_id = :org and sp.plan_date between :from and :to"
                + "), needed_agg as ("
                + "  select branch_id, sum(needed) as needed_total from slots group by branch_id"
                + "), assign as ("
                + "  select s.branch_id, sa.status, extract(epoch from (s.end_time - s.start_time)) / 3600.0 as hrs"
                + "  from slots s join shift_assignment sa on sa.slot_id = s.slot_id"
                + "), filled_agg as ("
                + "  select branch_id, count(*) filter (where status in ('CONFIRMED', 'SERVED')) as filled_ct,"
                + "         sum(hrs) filter (where status = 'SERVED') as hours_served"
                + "  from assign group by branch_id"
                + ") select b.name, coalesce(na.needed_total, 0), coalesce(fa.filled_ct, 0), round(coalesce(fa.hours_served, 0)::numeric, 1)"
                + " from branch b left join needed_agg na on na.branch_id = b.id left join filled_agg fa on fa.branch_id = b.id"
                + " where " + bw + " order by b.name";
        List<Object[]> rows = jdbc.query(sql, ps, (rs, i) -> new Object[]{rs.getString(1), rs.getLong(2), rs.getLong(3), rs.getDouble(4)});
        List<List<Object>> data = new ArrayList<>();
        long totalNeeded = 0;
        long totalFilled = 0;
        double totalHours = 0;
        for (Object[] r : rows) {
            data.add(List.of(r[0], r[1], r[2], r[3]));
            totalNeeded += (Long) r[1];
            totalFilled += (Long) r[2];
            totalHours += (Double) r[3];
        }
        return new ReportResult(columns(), data, Map.of("totalSlotsNeeded", totalNeeded, "totalSlotsFilled", totalFilled, "totalHoursServed", Math.round(totalHours * 10.0) / 10.0));
    }

    private static List<UUID> branchIdsOrNone(AccessScope scope) {
        return scope.branchIds() == null || scope.branchIds().isEmpty() ? List.of(new UUID(0, 0)) : List.copyOf(scope.branchIds());
    }
}
