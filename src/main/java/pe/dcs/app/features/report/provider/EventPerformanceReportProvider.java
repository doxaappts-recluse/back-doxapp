package pe.dcs.app.features.report.provider;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import pe.dcs.app.features.event.service.EventSupport;
import pe.dcs.app.features.report.spi.ReportProvider;
import pe.dcs.app.security.authz.AccessScope;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M20 · Doceavo reporte del catálogo (12 de las ~13), cubriendo "eventos" del spec ("inscritos, asistencia real,
 * ingresos vs costo") — se implementan inscritos, asistencia real e ingresos; "costo" queda fuera porque
 * {@code org_event} no tiene ningún campo de presupuesto/gasto en su esquema (los eventos no generan gasto
 * automático, solo ingreso vía la integración M14→M15 ya entregada) — agregarlo sería una función nueva del propio
 * M14/M15, no un ajuste de este reporte. Inscritos = registros con estado {@code REGISTERED} (excluye cancelados y
 * en espera); asistencia real = de esos, cuántos llegaron a usar su entrada ({@code ticket_used_at} no nulo, el
 * mismo campo que usa el check-in del propio M14); ingresos = suma de {@code amount} de los pagos {@code PAID}.
 */
@Component
@RequiredArgsConstructor
public class EventPerformanceReportProvider implements ReportProvider {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public String code() {
        return "EVENT_PERFORMANCE";
    }

    @Override
    public String moduleCode() {
        return EventSupport.MODULE;
    }

    @Override
    public String nameKey() {
        return "report.eventPerformance";
    }

    @Override
    public String chartType() {
        return "TABLE";
    }

    @Override
    public List<String> columns() {
        return List.of("branchName", "eventName", "registered", "attended", "income");
    }

    @Override
    public ReportResult run(AccessScope scope, Filters f) {
        MapSqlParameterSource ps = new MapSqlParameterSource("org", scope.organizationId())
                .addValue("from", java.sql.Date.valueOf(f.from())).addValue("to", java.sql.Date.valueOf(f.to()));
        StringBuilder w = new StringBuilder("e.organization_id = :org and e.start_at::date between :from and :to");
        if (!scope.allBranches()) {
            ps.addValue("branches", branchIdsOrNone(scope));
            w.append(" and e.branch_id in (:branches)");
        } else if (f.branchIds() != null && !f.branchIds().isEmpty()) {
            ps.addValue("fb", f.branchIds());
            w.append(" and e.branch_id in (:fb)");
        }
        String sql = "select coalesce(b.name, '—'), e.name,"
                + " count(r.id) filter (where r.status = 'REGISTERED'),"
                + " count(r.id) filter (where r.status = 'REGISTERED' and r.ticket_used_at is not null),"
                + " coalesce(sum(r.amount) filter (where r.payment_status = 'PAID'), 0)"
                + " from org_event e left join branch b on b.id = e.branch_id left join event_registration r on r.event_id = e.id"
                + " where " + w + " group by b.name, e.id, e.name order by b.name, e.name";
        List<Object[]> rows = jdbc.query(sql, ps, (rs, i) -> new Object[]{rs.getString(1), rs.getString(2), rs.getLong(3), rs.getLong(4), rs.getBigDecimal(5)});
        List<List<Object>> data = new ArrayList<>();
        long totalRegistered = 0;
        long totalAttended = 0;
        java.math.BigDecimal totalIncome = java.math.BigDecimal.ZERO;
        for (Object[] r : rows) {
            data.add(List.of(r[0], r[1], r[2], r[3], r[4]));
            totalRegistered += (Long) r[2];
            totalAttended += (Long) r[3];
            totalIncome = totalIncome.add((java.math.BigDecimal) r[4]);
        }
        return new ReportResult(columns(), data, Map.of("totalRegistered", totalRegistered, "totalAttended", totalAttended, "totalIncome", totalIncome));
    }

    private static List<UUID> branchIdsOrNone(AccessScope scope) {
        return scope.branchIds() == null || scope.branchIds().isEmpty() ? List.of(new UUID(0, 0)) : List.copyOf(scope.branchIds());
    }
}
