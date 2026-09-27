package pe.dcs.app.features.volunteer.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import pe.dcs.app.features.ministry.service.MinistrySupport;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.RoleType;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/** Utilidades de M11b: alcance de planes de servicio (OWN para quien lidera un ministerio con turnos en el plan) y carga de filas. Reusa {@link MinistrySupport} para
 * personas, elegibilidad [V5] y verificación [V6] (no se duplica esa lógica). */
@Component
@RequiredArgsConstructor
public class VolunteerSupport {

    public static final String MODULE = "VOLUNTEER_SCHEDULING";

    private final NamedParameterJdbcTemplate jdbc;
    private final MinistrySupport ministrySupport;

    public MinistrySupport ministry() {
        return ministrySupport;
    }

    public static boolean isOwn(AccessScope scope) {
        return scope.role() == RoleType.ORG_USER;
    }

    /** Alcance de sedes y, para quien lidera (OWN), solo los planes con algún turno de un ministerio que lidera. {@code a} es el alias de service_plan. */
    public static String visiblePlan(AccessScope scope, MapSqlParameterSource ps, String a) {
        StringBuilder sb = new StringBuilder(a + ".organization_id = :org");
        ps.addValue("org", scope.organizationId());
        if (!scope.allBranches()) {
            List<UUID> ids = scope.branchIds() == null || scope.branchIds().isEmpty() ? List.of(new UUID(0, 0)) : List.copyOf(scope.branchIds());
            ps.addValue("scopeBranches", ids);
            sb.append(" and ").append(a).append(".branch_id in (:scopeBranches)");
        }
        if (isOwn(scope)) {
            ps.addValue("meOwn", scope.personId() == null ? new UUID(0, 0) : scope.personId());
            sb.append(" and exists (select 1 from shift_slot ss join branch_ministry bm on bm.id = ss.branch_ministry_id where ss.plan_id = ").append(a)
                    .append(".id and bm.leader_person_id = :meOwn)");
        }
        return sb.toString();
    }

    public record PlanRow(UUID id, UUID orgId, UUID branchId, LocalDate planDate, String contextType, UUID contextId, String title, String status, long version) {
    }

    private static final String PLAN_SELECT = "select id, organization_id, branch_id, plan_date, context_type, context_id, title, status, version from service_plan";

    private static PlanRow plan(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new PlanRow((UUID) rs.getObject(1), (UUID) rs.getObject(2), (UUID) rs.getObject(3), rs.getDate(4).toLocalDate(), rs.getString(5), (UUID) rs.getObject(6),
                rs.getString(7), rs.getString(8), rs.getLong(9));
    }

    public PlanRow loadPlan(AccessScope scope, UUID id) {
        MapSqlParameterSource ps = new MapSqlParameterSource("id", id);
        String w = visiblePlan(scope, ps, "s");
        return jdbc.query(PLAN_SELECT.replace("service_plan", "service_plan s") + " where s.id = :id and " + w, ps, (rs, i) -> plan(rs)).stream().findFirst()
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    /** Bloquea la fila del plan (serializa la publicación y las asignaciones de sus turnos). */
    public PlanRow lockPlan(AccessScope scope, UUID id) {
        loadPlan(scope, id);
        jdbc.queryForList("select id from service_plan where id = :id for update", new MapSqlParameterSource("id", id));
        return loadPlanRaw(id);
    }

    public PlanRow loadPlanRaw(UUID id) {
        return jdbc.query(PLAN_SELECT + " where id = :id", new MapSqlParameterSource("id", id), (rs, i) -> plan(rs)).stream().findFirst()
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    public record SlotRow(UUID id, UUID planId, UUID branchMinistryId, UUID positionId, int needed, java.time.LocalTime start, java.time.LocalTime end, long version) {
    }

    public SlotRow loadSlot(UUID planId, UUID slotId) {
        return jdbc.query("select id, plan_id, branch_ministry_id, position_id, needed, start_time, end_time, version from shift_slot where id = :id and plan_id = :p",
                new MapSqlParameterSource("id", slotId).addValue("p", planId),
                (rs, i) -> new SlotRow((UUID) rs.getObject(1), (UUID) rs.getObject(2), (UUID) rs.getObject(3), (UUID) rs.getObject(4), rs.getInt(5), rs.getTime(6).toLocalTime(),
                        rs.getTime(7).toLocalTime(), rs.getLong(8))).stream().findFirst().orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    /** Zona horaria de la sede (para calcular el instante exacto de un turno), igual que AttendanceSupport.zoneOf pero accesible desde este paquete. */
    public ZoneId zoneOf(UUID branchId) {
        List<String> z = jdbc.queryForList("select coalesce(b.timezone, o.timezone) from branch b join organization o on o.id = b.organization_id where b.id = :b",
                new MapSqlParameterSource("b", branchId), String.class);
        try {
            return z.isEmpty() || z.get(0) == null ? ZoneId.of("America/Lima") : ZoneId.of(z.get(0));
        } catch (RuntimeException e) {
            return ZoneId.of("America/Lima");
        }
    }

    public boolean leads(AccessScope scope, UUID branchMinistryId) {
        if (!isOwn(scope)) {
            return true;
        }
        List<UUID> r = jdbc.queryForList("select leader_person_id from branch_ministry where id = :id", new MapSqlParameterSource("id", branchMinistryId), UUID.class);
        return !r.isEmpty() && scope.personId() != null && scope.personId().equals(r.get(0));
    }
}
