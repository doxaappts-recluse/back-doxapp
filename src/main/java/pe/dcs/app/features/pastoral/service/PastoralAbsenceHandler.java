package pe.dcs.app.features.pastoral.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import pe.dcs.app.features.attendance.service.AbsenceAlertParticipant;
import pe.dcs.app.features.notification.domain.NotificationType;
import pe.dcs.app.features.notification.service.NotificationService;
import pe.dcs.app.features.pastoral.dto.PastoralDtos;
import pe.dcs.app.shared.audit.AuditService;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M12 en M09: cada alerta de inasistencia (una por racha) abre un caso pastoral automático (INACTIVE_FOLLOWUP, source
 * AUTO_ABSENCE) sin responsable, para que la sede lo tome [T01] [T08]. Nunca abre dos a la vez por persona [V8]: lo garantiza
 * el índice único parcial {@code ux_pc_auto_open} además de esta comprobación.
 */
@Component
@RequiredArgsConstructor
public class PastoralAbsenceHandler implements AbsenceAlertParticipant {

    private static final String MODULE = "PASTORAL_CARE";

    private final NamedParameterJdbcTemplate jdbc;
    private final PastoralRulesService rules;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Clock clock;

    @Override
    public void onAbsenceAlert(UUID orgId, UUID branchId, UUID personId, LocalDate lastAttended, int weeks, UUID alertId) {
        Integer open = jdbc.queryForObject("select count(*) from pastoral_case where person_id = :p and source = 'AUTO_ABSENCE' and status in ('OPEN','IN_PROGRESS')",
                new MapSqlParameterSource("p", personId), Integer.class);
        if (open != null && open > 0) {
            return;
        }
        PastoralDtos.RulesResponse r = rules.get(orgId);
        Timestamp now = Timestamp.from(clock.instant());
        Timestamp due = Timestamp.from(clock.instant().plusSeconds(r.slaHoursNormal() * 3600L));
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("insert into pastoral_case (id, organization_id, branch_id, person_id, type, priority, status, source, confidentiality,"
                            + " due_at, created_at, updated_at) values (:id, :o, :b, :p, 'INACTIVE_FOLLOWUP', 'NORMAL', 'OPEN', 'AUTO_ABSENCE',"
                            + " 'STANDARD', :due, :at, :at)",
                    new MapSqlParameterSource("id", id).addValue("o", orgId).addValue("b", branchId).addValue("p", personId).addValue("due", due).addValue("at", now));
        } catch (DataIntegrityViolationException e) {
            return;
        }
        audit.record(new AuditService.Command(MODULE, "AUTO_CREATE", "PastoralCase", id, orgId, branchId, Map.of("weeks", weeks)));
        String person = jdbc.queryForList("select trim(first_name || ' ' || last_name) from person where id = :id", new MapSqlParameterSource("id", personId), String.class)
                .stream().findFirst().orElse("");
        String branch = jdbc.queryForList("select name from branch where id = :id", new MapSqlParameterSource("id", branchId), String.class)
                .stream().findFirst().orElse("");
        notifications.toPersons(NotificationType.PASTORAL_CASE_OPENED, orgId, notifications.branchAdmins(orgId, branchId),
                Map.of("person", person, "weeks", String.valueOf(weeks), "branch", branch), "/app/pastoral-cases/" + id, "pastoral-auto:" + id);
    }
}
