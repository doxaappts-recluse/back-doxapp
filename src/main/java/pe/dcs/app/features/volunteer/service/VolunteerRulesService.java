package pe.dcs.app.features.volunteer.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.volunteer.dto.VolunteerDtos;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.AuthorizationService;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.RoleType;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** M11b · Reglas de voluntariado por organización: máximo de turnos al mes, horas de los recordatorios y ventana en la que rechazar exige motivo [V12]. Solo el
 * administrador de la organización las edita (spec "ORG_ADMIN ... + VolunteerRules"). */
@Service
@RequiredArgsConstructor
public class VolunteerRulesService {

    static final int DEFAULT_MAX_PER_MONTH = 4;
    static final int DEFAULT_REMINDER_1 = 48;
    static final int DEFAULT_REMINDER_2 = 2;
    static final int DEFAULT_DECLINE_LOCK = 24;

    private final NamedParameterJdbcTemplate jdbc;
    private final AuthorizationService authz;
    private final AuditService audit;
    private final Clock clock;

    @Transactional(readOnly = true)
    public VolunteerDtos.RulesResponse get(UUID orgId) {
        return jdbc.query("select max_shifts_per_month, reminder_hours_1, reminder_hours_2, decline_lock_hours, version from volunteer_rules where organization_id = :o",
                new MapSqlParameterSource("o", orgId), (rs, i) -> new VolunteerDtos.RulesResponse(rs.getInt(1), (Integer) rs.getObject(2), (Integer) rs.getObject(3), rs.getInt(4), rs.getLong(5)))
                .stream().findFirst().orElse(new VolunteerDtos.RulesResponse(DEFAULT_MAX_PER_MONTH, DEFAULT_REMINDER_1, DEFAULT_REMINDER_2, DEFAULT_DECLINE_LOCK, null));
    }

    @Transactional
    public VolunteerDtos.RulesResponse update(AuthenticatedActor actor, AccessScope scope, VolunteerDtos.RulesRequest r) {
        authz.require(actor, VolunteerSupport.MODULE, Action.E);
        if (scope.role() != RoleType.ORG_ADMIN) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
        if (r == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "datos");
        }
        int max = range(r.maxShiftsPerMonth(), 1, 60, "turnos por mes");
        Integer r1 = r.reminderHours1() == null ? null : range(r.reminderHours1(), 1, 336, "primer recordatorio");
        Integer r2 = r.reminderHours2() == null ? null : range(r.reminderHours2(), 1, 336, "segundo recordatorio");
        int lock = range(r.declineLockHours(), 0, 336, "ventana para rechazar");
        VolunteerDtos.RulesResponse cur = get(scope.organizationId());
        MapSqlParameterSource ps = new MapSqlParameterSource("o", scope.organizationId()).addValue("m", max).addValue("r1", r1).addValue("r2", r2).addValue("l", lock)
                .addValue("at", Timestamp.from(clock.instant())).addValue("by", actor.ownerId());
        if (cur.version() == null) {
            jdbc.update("insert into volunteer_rules (organization_id, max_shifts_per_month, reminder_hours_1, reminder_hours_2, decline_lock_hours, updated_at, updated_by)"
                    + " values (:o, :m, :r1, :r2, :l, :at, :by)", ps);
        } else {
            if (r.version() != null && !r.version().equals(cur.version())) {
                throw new Exceptions("error.common.concurrentUpdate", HttpStatus.CONFLICT);
            }
            jdbc.update("update volunteer_rules set max_shifts_per_month = :m, reminder_hours_1 = :r1, reminder_hours_2 = :r2, decline_lock_hours = :l,"
                    + " updated_at = :at, updated_by = :by, version = version + 1 where organization_id = :o", ps);
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("maxShiftsPerMonth", max);
        d.put("declineLockHours", lock);
        audit.record(new AuditService.Command(VolunteerSupport.MODULE, "RULES", "VolunteerRules", scope.organizationId(), scope.organizationId(), null, d));
        return get(scope.organizationId());
    }

    private static int range(Integer v, int min, int max, String label) {
        if (v == null || v < min || v > max) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, label);
        }
        return v;
    }
}
