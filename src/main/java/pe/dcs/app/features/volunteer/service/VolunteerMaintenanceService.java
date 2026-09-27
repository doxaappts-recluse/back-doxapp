package pe.dcs.app.features.volunteer.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.notification.domain.NotificationType;
import pe.dcs.app.features.notification.service.NotificationService;
import pe.dcs.app.features.volunteer.dto.VolunteerDtos;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M11b · Tarea periódica: recordatorios de turno [T07] a quienes tienen una asignación PROPOSED o CONFIRMED en un plan PUBLISHED, en las horas configuradas por la
 * organización (por defecto 48 h y 2 h antes). Cada recordatorio se emite una sola vez por turno y persona (clave de deduplicación) y nunca a quien ya rechazó.
 */
@Service
@RequiredArgsConstructor
public class VolunteerMaintenanceService {

    private final NamedParameterJdbcTemplate jdbc;
    private final VolunteerSupport support;
    private final VolunteerRulesService rules;
    private final NotificationService notifications;
    private final Clock clock;

    @Transactional
    public Map<String, Integer> run() {
        List<Object[]> rows = jdbc.query("select a.id, a.person_id, sl.start_time, pl.plan_date, pl.branch_id, pl.organization_id, m.name"
                        + " from shift_assignment a join shift_slot sl on sl.id = a.slot_id join service_plan pl on pl.id = sl.plan_id"
                        + " join branch_ministry bm on bm.id = sl.branch_ministry_id join ministry m on m.id = bm.ministry_id"
                        + " where a.status in ('PROPOSED', 'CONFIRMED') and pl.status = 'PUBLISHED' and pl.plan_date between current_date and current_date + 10",
                new MapSqlParameterSource(), (rs, i) -> new Object[]{rs.getObject(1), rs.getObject(2), rs.getTime(3).toLocalTime(), rs.getDate(4).toLocalDate(),
                        rs.getObject(5), rs.getObject(6), rs.getString(7)});
        Instant now = clock.instant();
        int reminded = 0;
        for (Object[] row : rows) {
            UUID assignmentId = (UUID) row[0];
            UUID personId = (UUID) row[1];
            LocalTime start = (LocalTime) row[2];
            LocalDate date = (LocalDate) row[3];
            UUID branchId = (UUID) row[4];
            UUID orgId = (UUID) row[5];
            String ministry = (String) row[6];
            ZoneId zone = support.zoneOf(branchId);
            Instant shiftStart = date.atTime(start).atZone(zone).toInstant();
            if (!shiftStart.isAfter(now)) {
                continue;
            }
            VolunteerDtos.RulesResponse rr = rules.get(orgId);
            reminded += remindIfDue(rr.reminderHours1(), assignmentId, personId, orgId, ministry, date, start, shiftStart, now);
            reminded += remindIfDue(rr.reminderHours2(), assignmentId, personId, orgId, ministry, date, start, shiftStart, now);
        }
        Map<String, Integer> out = new LinkedHashMap<>();
        out.put("reminded", reminded);
        return out;
    }

    private int remindIfDue(Integer tierHours, UUID assignmentId, UUID personId, UUID orgId, String ministry, LocalDate date, LocalTime start, Instant shiftStart, Instant now) {
        if (tierHours == null) {
            return 0;
        }
        Instant windowStart = shiftStart.minus(Duration.ofHours(tierHours));
        if (now.isBefore(windowStart)) {
            return 0;
        }
        String dedupe = "SHIFT_REMINDER:" + tierHours + ":" + assignmentId;
        return notifications.toPersons(NotificationType.SHIFT_REMINDER, orgId, List.of(personId),
                Map.of("ministry", ministry, "date", date.toString(), "time", start.toString()), "/app/volunteer-scheduling", dedupe);
    }
}
