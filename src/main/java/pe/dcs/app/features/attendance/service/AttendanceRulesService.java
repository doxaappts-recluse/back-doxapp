package pe.dcs.app.features.attendance.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.attendance.dto.AttendanceDtos;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.RoleType;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** M09 · Reglas de asistencia por organización. [V1] enteros ≥ 1; edad máxima de niños 1–17. Solo las edita el administrador de la organización. */
@Service
@RequiredArgsConstructor
public class AttendanceRulesService {

    static final int DEFAULT_ABSENCE_WEEKS = 4;
    static final int DEFAULT_CHILD_MAX_AGE = 12;
    static final int DEFAULT_QR_TTL = 30;
    static final int DEFAULT_REOPEN_DAYS = 7;

    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService audit;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AttendanceDtos.Rules get(UUID orgId) {
        return jdbc.query("select absence_weeks_alert, child_max_age, qr_ttl_seconds, reopen_days, version from attendance_rules where organization_id = :o",
                new MapSqlParameterSource("o", orgId),
                (rs, i) -> new AttendanceDtos.Rules(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getLong(5))).stream().findFirst()
                .orElse(new AttendanceDtos.Rules(DEFAULT_ABSENCE_WEEKS, DEFAULT_CHILD_MAX_AGE, DEFAULT_QR_TTL, DEFAULT_REOPEN_DAYS, null));
    }

    @Transactional
    public AttendanceDtos.Rules update(AuthenticatedActor actor, AccessScope scope, AttendanceDtos.RulesRequest r) {
        if (scope.role() != RoleType.ORG_ADMIN) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
        if (r == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "datos");
        }
        int weeks = range(r.absenceWeeksAlert(), 1, 104, "semanas sin asistir");
        int age = range(r.childMaxAge(), 1, 17, "edad máxima de niños");
        int ttl = range(r.qrTtlSeconds(), 1, 3600, "vigencia del QR");
        int reopen = range(r.reopenDays(), 1, 365, "días para reabrir");
        AttendanceDtos.Rules cur = get(scope.organizationId());
        MapSqlParameterSource ps = new MapSqlParameterSource("o", scope.organizationId()).addValue("w", weeks).addValue("a", age).addValue("t", ttl)
                .addValue("r", reopen).addValue("at", Timestamp.from(clock.instant())).addValue("by", actor.ownerId());
        if (cur.version() == null) {
            jdbc.update("insert into attendance_rules (organization_id, absence_weeks_alert, child_max_age, qr_ttl_seconds, reopen_days, updated_at, updated_by)"
                    + " values (:o, :w, :a, :t, :r, :at, :by)", ps);
        } else {
            if (r.version() != null && !r.version().equals(cur.version())) {
                throw new Exceptions("error.common.concurrentUpdate", HttpStatus.CONFLICT);
            }
            jdbc.update("update attendance_rules set absence_weeks_alert = :w, child_max_age = :a, qr_ttl_seconds = :t, reopen_days = :r,"
                    + " updated_at = :at, updated_by = :by, version = version + 1 where organization_id = :o", ps);
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("absenceWeeksAlert", weeks);
        d.put("childMaxAge", age);
        d.put("qrTtlSeconds", ttl);
        d.put("reopenDays", reopen);
        audit.record(new AuditService.Command("ATTENDANCE", "RULES", "AttendanceRules", scope.organizationId(), scope.organizationId(), null, d));
        return get(scope.organizationId());
    }

    private static int range(Integer v, int min, int max, String label) {
        if (v == null || v < min || v > max) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, label);
        }
        return v;
    }
}
