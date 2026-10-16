package pe.dcs.app.features.pastoral.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.notification.domain.NotificationType;
import pe.dcs.app.features.notification.service.NotificationService;
import pe.dcs.app.shared.audit.AuditService;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M12 · Tarea automática (cada hora y a pedido): casos con SLA vencido avisan a su responsable (o a la sede sin responsable);
 * si el vencimiento se duplica sin resolverse, se escala a los administradores de la sede [T06].
 */
@Service
@RequiredArgsConstructor
public class PastoralMaintenanceService {

    private static final String MODULE = "PASTORAL_CARE";

    private final NamedParameterJdbcTemplate jdbc;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Clock clock;

    @Transactional
    public Map<String, Integer> run() {
        Map<String, Integer> r = new LinkedHashMap<>();
        r.put("slaWarnings", slaWarnings());
        r.put("slaEscalations", slaEscalations());
        return r;
    }

    record Due(UUID id, UUID org, UUID branch, UUID person, UUID assignedTo, Instant dueAt, int hours) {
    }

    @Transactional
    public int slaWarnings() {
        Instant now = clock.instant();
        List<Due> due = jdbc.query("""
                select c.id, c.organization_id, c.branch_id, c.person_id, c.assigned_to, c.due_at,
                       case c.priority when 'HIGH' then coalesce(r.sla_hours_high, 24) when 'LOW' then coalesce(r.sla_hours_low, 168)
                            else coalesce(r.sla_hours_normal, 72) end as hours
                  from pastoral_case c left join pastoral_rules r on r.organization_id = c.organization_id
                 where c.status in ('OPEN','IN_PROGRESS') and c.due_at is not null and c.due_at < :now and c.sla_alerted_at is null""",
                new MapSqlParameterSource("now", Timestamp.from(now)),
                (rs, i) -> new Due((UUID) rs.getObject(1), (UUID) rs.getObject(2), (UUID) rs.getObject(3), (UUID) rs.getObject(4), (UUID) rs.getObject(5),
                        rs.getTimestamp(6).toInstant(), rs.getInt(7)));
        int n = 0;
        for (Due d : due) {
            List<UUID> to = d.assignedTo() != null ? List.of(d.assignedTo()) : notifications.branchAdmins(d.org(), d.branch());
            notifications.toPersons(NotificationType.PASTORAL_SLA_WARNING, d.org(), to,
                    Map.of("person", personName(d.person()), "branch", branchName(d.branch())), "/app/pastoral-cases/" + d.id(), "pastoral-sla:" + d.id());
            jdbc.update("update pastoral_case set sla_alerted_at = :now where id = :id", new MapSqlParameterSource("now", Timestamp.from(now)).addValue("id", d.id()));
            audit.record(new AuditService.Command(MODULE, "SLA_WARNING", "PastoralCase", d.id(), d.org(), d.branch(), Map.of()));
            n++;
        }
        return n;
    }

    /** Escala cuando pasa el doble del plazo original sin resolverse [T06]. */
    @Transactional
    public int slaEscalations() {
        Instant now = clock.instant();
        List<Due> due = jdbc.query("""
                select c.id, c.organization_id, c.branch_id, c.person_id, c.assigned_to, c.due_at,
                       case c.priority when 'HIGH' then coalesce(r.sla_hours_high, 24) when 'LOW' then coalesce(r.sla_hours_low, 168)
                            else coalesce(r.sla_hours_normal, 72) end as hours
                  from pastoral_case c left join pastoral_rules r on r.organization_id = c.organization_id
                 where c.status in ('OPEN','IN_PROGRESS') and c.due_at is not null and c.sla_escalated_at is null
                   and c.due_at + (case c.priority when 'HIGH' then coalesce(r.sla_hours_high, 24) when 'LOW' then coalesce(r.sla_hours_low, 168)
                                        else coalesce(r.sla_hours_normal, 72) end || ' hours')::interval < :now""",
                new MapSqlParameterSource("now", Timestamp.from(now)),
                (rs, i) -> new Due((UUID) rs.getObject(1), (UUID) rs.getObject(2), (UUID) rs.getObject(3), (UUID) rs.getObject(4), (UUID) rs.getObject(5),
                        rs.getTimestamp(6).toInstant(), rs.getInt(7)));
        int n = 0;
        for (Due d : due) {
            List<UUID> to = new ArrayList<>(notifications.branchAdmins(d.org(), d.branch()));
            notifications.toPersons(NotificationType.PASTORAL_ESCALATED, d.org(), to,
                    Map.of("person", personName(d.person()), "branch", branchName(d.branch())), "/app/pastoral-cases/" + d.id(), "pastoral-esc:" + d.id());
            jdbc.update("update pastoral_case set sla_escalated_at = :now where id = :id", new MapSqlParameterSource("now", Timestamp.from(now)).addValue("id", d.id()));
            audit.record(new AuditService.Command(MODULE, "SLA_ESCALATED", "PastoralCase", d.id(), d.org(), d.branch(), Map.of()));
            n++;
        }
        return n;
    }

    private String personName(UUID id) {
        List<String> n = jdbc.queryForList("select trim(first_name || ' ' || last_name) from person where id = :id", new MapSqlParameterSource("id", id), String.class);
        return n.isEmpty() ? "" : n.get(0);
    }

    private String branchName(UUID id) {
        List<String> n = jdbc.queryForList("select name from branch where id = :id", new MapSqlParameterSource("id", id), String.class);
        return n.isEmpty() ? "" : n.get(0);
    }
}
