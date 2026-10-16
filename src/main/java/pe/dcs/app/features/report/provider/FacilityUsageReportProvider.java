package pe.dcs.app.features.report.provider;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import pe.dcs.app.features.facility.service.FacilitySupport;
import pe.dcs.app.features.report.spi.ReportProvider;
import pe.dcs.app.security.authz.AccessScope;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M20 · Octavo reporte del catálogo (8 de las ~13 del spec), cubriendo la categoría "recursos" que quedaba sin
 * representante — el spec pide "uso de espacios, inventario valorizado"; se implementa el primero (uso de espacios),
 * mismo criterio del resto del catálogo de tomar un ángulo representativo por categoría en vez de las N variantes que
 * lista el spec (p. ej. PERSON_GROWTH solo cubre "crecimiento" de las cuatro métricas de personas). El inventario
 * valorizado de M16 queda como una posible extensión futura, igual que las demás categorías aún sin construir.
 * <p>
 * Cuenta reservas CONFIRMED por espacio (una reserva CANCELLED no refleja uso real, mismo criterio que
 * FINANCE_SUMMARY solo contando movimientos APPROVED) y suma las horas reservadas. Se muestran TODOS los espacios
 * ACTIVE/MAINTENANCE/INACTIVE de la sede (no solo los que tuvieron reservas), para que un espacio nunca reservado en
 * el rango también aparezca con cero — información tan útil como la de los que sí se usan. La franja horaria de cada
 * reserva se interpreta en America/Lima (el resto del catálogo de M20 filtra por columnas DATE simples sin huso
 * horario; reservation.start_at es TIMESTAMPTZ, así que aquí sí hace falta una conversión explícita — se usa el
 * mismo huso por omisión que GroupSupport/FacilitySupport/EventSupport ya usan cuando la sede no tiene uno propio).
 */
@Component
@RequiredArgsConstructor
public class FacilityUsageReportProvider implements ReportProvider {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public String code() {
        return "FACILITY_USAGE";
    }

    @Override
    public String moduleCode() {
        return FacilitySupport.MOD_SPACES;
    }

    @Override
    public String nameKey() {
        return "report.facilityUsage";
    }

    @Override
    public String chartType() {
        return "BAR";
    }

    @Override
    public List<String> columns() {
        return List.of("branchName", "spaceName", "capacity", "reservationsCount", "hoursReserved");
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
        String sql = "with used as ("
                + "  select r.space_id as space_id, count(*) as reservations_count,"
                + "         sum(extract(epoch from (r.end_at - r.start_at)) / 3600.0) as hours"
                + "  from reservation r"
                + "  where r.organization_id = :org and r.status = 'CONFIRMED'"
                + "    and (r.start_at at time zone 'America/Lima')::date between :from and :to"
                + "  group by r.space_id"
                + ") select b.name, sp.name, sp.capacity, coalesce(u.reservations_count, 0), round(coalesce(u.hours, 0)::numeric, 1)"
                + " from space sp join branch b on b.id = sp.branch_id left join used u on u.space_id = sp.id"
                + " where sp.organization_id = :org and " + bw
                + " order by b.name, sp.name";
        List<Object[]> rows = jdbc.query(sql, ps, (rs, i) -> new Object[]{rs.getString(1), rs.getString(2), rs.getObject(3), rs.getLong(4), rs.getDouble(5)});
        List<List<Object>> data = new ArrayList<>();
        long totalReservations = 0;
        double totalHours = 0;
        for (Object[] r : rows) {
            data.add(List.of(r[0], r[1], r[2] == null ? "" : r[2], r[3], r[4]));
            totalReservations += (Long) r[3];
            totalHours += (Double) r[4];
        }
        return new ReportResult(columns(), data, Map.of("totalReservations", totalReservations, "totalHours", Math.round(totalHours * 10.0) / 10.0));
    }

    private static List<UUID> branchIdsOrNone(AccessScope scope) {
        return scope.branchIds() == null || scope.branchIds().isEmpty() ? List.of(new UUID(0, 0)) : List.copyOf(scope.branchIds());
    }
}
