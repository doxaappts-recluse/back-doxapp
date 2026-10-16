package pe.dcs.app.features.visitor.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.visitor.dto.VisitorDtos;
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

/** M07 · Reglas de visitantes por organización (umbrales de integración y plazo de primer contacto). Solo las edita el administrador de la organización. */
@Service
@RequiredArgsConstructor
public class VisitorRulesService {

    static final int DEFAULT_ATTENDANCES = 3;
    static final int DEFAULT_WEEKS = 6;
    static final int DEFAULT_SLA_HOURS = 48;
    private static final int MAX = 1000;

    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService audit;
    private final Clock clock;

    @Transactional(readOnly = true)
    public VisitorDtos.Rules get(UUID orgId) {
        return jdbc.query("select integrated_min_attendances, integrated_window_weeks, new_sla_hours, version from visitor_rules where organization_id = :o",
                new MapSqlParameterSource("o", orgId),
                (rs, i) -> new VisitorDtos.Rules(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getLong(4))).stream().findFirst()
                .orElse(new VisitorDtos.Rules(DEFAULT_ATTENDANCES, DEFAULT_WEEKS, DEFAULT_SLA_HOURS, null));
    }

    @Transactional
    public VisitorDtos.Rules update(AuthenticatedActor actor, AccessScope scope, VisitorDtos.RulesRequest r) {
        if (scope.role() != RoleType.ORG_ADMIN) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
        int att = positive(r == null ? null : r.integratedMinAttendances(), "asistencias");                         // [V1]
        int weeks = positive(r.integratedWindowWeeks(), "semanas");
        int sla = positive(r.newSlaHours(), "horas");
        VisitorDtos.Rules cur = get(scope.organizationId());
        MapSqlParameterSource ps = new MapSqlParameterSource("o", scope.organizationId()).addValue("a", att).addValue("w", weeks).addValue("s", sla)
                .addValue("at", Timestamp.from(clock.instant())).addValue("by", actor.ownerId());
        if (cur.version() == null) {
            jdbc.update("insert into visitor_rules (organization_id, integrated_min_attendances, integrated_window_weeks, new_sla_hours, updated_at, updated_by)"
                    + " values (:o, :a, :w, :s, :at, :by)", ps);
        } else {
            if (r.version() != null && !r.version().equals(cur.version())) {
                throw new Exceptions("error.common.concurrentUpdate", HttpStatus.CONFLICT);
            }
            jdbc.update("update visitor_rules set integrated_min_attendances = :a, integrated_window_weeks = :w, new_sla_hours = :s,"
                    + " updated_at = :at, updated_by = :by, version = version + 1 where organization_id = :o", ps);
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("attendances", att);
        d.put("weeks", weeks);
        d.put("slaHours", sla);
        audit.record(new AuditService.Command("VISITOR", "RULES", "VisitorRules", scope.organizationId(), scope.organizationId(), null, d));
        return get(scope.organizationId());
    }

    private static int positive(Integer v, String label) {
        if (v == null || v < 1 || v > MAX) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, label);
        }
        return v;
    }
}
