package pe.dcs.app.features.attendance.service;

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
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M09 · Tareas automáticas (cada hora y a pedido): crear la sesión del día de cada culto activo (fecha local de la sede, no UTC), cerrar sesiones que
 * quedaron abiertas más de 24 h después de terminar y detectar rachas de inasistencia (una alerta por racha; M12 las convertirá en tarea).
 */
@Service
@RequiredArgsConstructor
public class AttendanceMaintenanceService {

    private final NamedParameterJdbcTemplate jdbc;
    private final AttendanceService attendance;
    private final AttendanceSupport support;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Clock clock;
    private final List<AbsenceAlertParticipant> participants;

    @Transactional
    public Map<String, Integer> run() {
        Map<String, Integer> r = new LinkedHashMap<>();
        r.put("sessionsCreated", createDailySessions());
        r.put("sessionsClosed", closeStale());
        r.put("absenceAlerts", absenceAlerts());
        return r;
    }

    /** Sesión de hoy (hora local de la sede) para cada culto semanal activo cuyo día coincide. */
    @Transactional
    public int createDailySessions() {
        record Svc(UUID id, UUID orgId, UUID branchId, String name, int dow, LocalTime start, int dur, boolean self) {
        }
        List<Svc> list = jdbc.query("select s.id, s.organization_id, s.branch_id, s.name, s.day_of_week, s.start_time, s.duration_min, s.self_checkin_enabled"
                        + " from church_service s join branch b on b.id = s.branch_id join organization o on o.id = s.organization_id"
                        + " where s.status = 'ACTIVE' and s.recurrence = 'WEEKLY' and b.status = 'ACTIVE' and o.status in ('ACTIVE','TRIAL')", new MapSqlParameterSource(),
                (rs, i) -> new Svc((UUID) rs.getObject(1), (UUID) rs.getObject(2), (UUID) rs.getObject(3), rs.getString(4), rs.getInt(5), rs.getTime(6).toLocalTime(), rs.getInt(7),
                        rs.getBoolean(8)));
        int created = 0;
        for (Svc s : list) {
            ZoneId zone = support.zoneOf(s.branchId());
            LocalDate today = LocalDate.now(clock.withZone(zone));
            if (today.getDayOfWeek() != DayOfWeek.of(s.dow())) {
                continue;
            }
            UUID id = attendance.insertSession(s.orgId(), s.branchId(), s.id(), s.name(), s.start(), s.dur(), s.self(), today, "JOB", null);
            if (id != null) {
                created++;
                audit.record(new AuditService.Command("ATTENDANCE", "SESSION_CREATE", "AttendanceSession", id, s.orgId(), s.branchId(),
                        Map.of("service", s.name(), "date", today.toString(), "origin", "JOB")));
            }
        }
        return created;
    }

    /** Sesiones que siguen abiertas 24 h después de su hora de fin se cierran solas. */
    @Transactional
    public int closeStale() {
        Timestamp limit = Timestamp.from(clock.instant().minus(Duration.ofHours(24)));
        List<Object[]> rows = jdbc.query("select id, organization_id, branch_id from attendance_session where status = 'OPEN' and ends_at < :l", new MapSqlParameterSource("l", limit),
                (rs, i) -> new Object[]{rs.getObject(1), rs.getObject(2), rs.getObject(3)});
        for (Object[] r : rows) {
            jdbc.update("update attendance_session set status = 'CLOSED', closed_at = :at, version = version + 1 where id = :id and status = 'OPEN'",
                    new MapSqlParameterSource("id", r[0]).addValue("at", Timestamp.from(clock.instant())));
            audit.record(new AuditService.Command("ATTENDANCE", "SESSION_CLOSE", "AttendanceSession", r[0], (UUID) r[1], (UUID) r[2], Map.of("auto", true)));
        }
        return rows.size();
    }

    /**
     * Personas activas que asistieron alguna vez a un culto y llevan {@code absenceWeeksAlert} semanas o más sin asistir, en una sede que sí
     * tomó asistencia en ese lapso. Una sola alerta abierta por persona; una asistencia nueva la cierra y reinicia la racha.
     */
    @Transactional
    public int absenceAlerts() {
        record Cand(UUID personId, UUID orgId, UUID branchId, LocalDate last, int weeks, String name, String branchName) {
        }
        List<Cand> cands = jdbc.query("""
                select p.id, p.organization_id, p.primary_branch_id, l.d, ((now() at time zone o.timezone)::date - l.d) / 7 as weeks,
                       trim(p.first_name || ' ' || p.last_name) as name, b.name as bn
                  from person p
                  join organization o on o.id = p.organization_id
                  join branch b on b.id = p.primary_branch_id
                  left join attendance_rules r on r.organization_id = p.organization_id
                  join lateral (select max(s.session_date) as d from attendance_record ar join attendance_session s on s.id = ar.session_id
                                 where ar.person_id = p.id and ar.status in ('PRESENT','LATE') and s.context_type = 'SERVICE') l on l.d is not null
                 where p.status = 'ACTIVE' and p.anonymized_at is null and o.status in ('ACTIVE','TRIAL')
                   and l.d <= (now() at time zone o.timezone)::date - coalesce(r.absence_weeks_alert, 4) * 7
                   and exists (select 1 from attendance_session s2 where s2.branch_id = p.primary_branch_id and s2.context_type = 'SERVICE' and s2.status = 'CLOSED'
                                  and s2.session_date >= (now() at time zone o.timezone)::date - coalesce(r.absence_weeks_alert, 4) * 7)
                   and not exists (select 1 from attendance_absence_alert a where a.person_id = p.id and a.status = 'OPEN')""",
                new MapSqlParameterSource(), (rs, i) -> new Cand((UUID) rs.getObject(1), (UUID) rs.getObject(2), (UUID) rs.getObject(3), rs.getDate(4).toLocalDate(), rs.getInt(5),
                        rs.getString(6), rs.getString(7)));
        int n = 0;
        for (Cand c : cands) {
            UUID id = UUID.randomUUID();
            int ins = jdbc.update("insert into attendance_absence_alert (id, organization_id, branch_id, person_id, last_attended, weeks, created_at) values (:id, :o, :b, :p, :l, :w, :at)"
                            + " on conflict do nothing",
                    new MapSqlParameterSource("id", id).addValue("o", c.orgId()).addValue("b", c.branchId()).addValue("p", c.personId()).addValue("l", java.sql.Date.valueOf(c.last()))
                            .addValue("w", c.weeks()).addValue("at", Timestamp.from(clock.instant())));
            if (ins == 0) {
                continue;
            }
            n++;
            notifications.toPersons(NotificationType.ATTENDANCE_ABSENCE, c.orgId(), notifications.branchAdmins(c.orgId(), c.branchId()),
                    Map.of("person", c.name(), "weeks", String.valueOf(c.weeks()), "branch", c.branchName()), "/app/persons/" + c.personId(), "abs:" + id);
            audit.record(new AuditService.Command("ATTENDANCE", "ABSENCE_ALERT", "AttendanceAbsenceAlert", id, c.orgId(), c.branchId(), Map.of("weeks", c.weeks())));
            for (AbsenceAlertParticipant p : participants) {
                p.onAbsenceAlert(c.orgId(), c.branchId(), c.personId(), c.last(), c.weeks(), id);
            }
        }
        return n;
    }
}
