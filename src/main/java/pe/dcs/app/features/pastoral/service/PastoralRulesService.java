package pe.dcs.app.features.pastoral.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.pastoral.dto.PastoralDtos;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.util.Exceptions;

import java.util.UUID;

/** M12 · Reglas por organización: semanas de inactividad para abrir un caso automático y horas de SLA por prioridad [V1]. */
@Service
@RequiredArgsConstructor
public class PastoralRulesService {

    private static final String MODULE = "PASTORAL_CARE";

    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public PastoralDtos.RulesResponse get(UUID orgId) {
        return jdbc.query("select absence_weeks, sla_hours_high, sla_hours_normal, sla_hours_low, version from pastoral_rules where organization_id = :o",
                new MapSqlParameterSource("o", orgId), (rs, i) -> new PastoralDtos.RulesResponse(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getLong(5)))
                .stream().findFirst().orElseGet(() -> new PastoralDtos.RulesResponse(4, 24, 72, 168, 0L));
    }

    @Transactional
    public PastoralDtos.RulesResponse update(AuthenticatedActor actor, AccessScope scope, PastoralDtos.RulesRequest r) {
        if (r == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "datos");
        }
        int weeks = pos(r.absenceWeeks(), 4);
        int high = pos(r.slaHoursHigh(), 24);
        int normal = pos(r.slaHoursNormal(), 72);
        int low = pos(r.slaHoursLow(), 168);
        Long current = jdbc.query("select version from pastoral_rules where organization_id = :o", new MapSqlParameterSource("o", scope.organizationId()),
                (rs, i) -> rs.getLong(1)).stream().findFirst().orElse(null);
        if (current == null) {
            jdbc.update("insert into pastoral_rules (organization_id, absence_weeks, sla_hours_high, sla_hours_normal, sla_hours_low, version)"
                            + " values (:o, :w, :h, :n, :l, 0)",
                    new MapSqlParameterSource("o", scope.organizationId()).addValue("w", weeks).addValue("h", high).addValue("n", normal).addValue("l", low));
        } else {
            if (r.version() != null && !r.version().equals(current)) {
                throw new Exceptions("error.common.concurrentUpdate", HttpStatus.CONFLICT);
            }
            jdbc.update("update pastoral_rules set absence_weeks = :w, sla_hours_high = :h, sla_hours_normal = :n, sla_hours_low = :l,"
                            + " version = version + 1 where organization_id = :o",
                    new MapSqlParameterSource("o", scope.organizationId()).addValue("w", weeks).addValue("h", high).addValue("n", normal).addValue("l", low));
        }
        audit.record(new AuditService.Command(MODULE, "RULES_UPDATE", "PastoralRules", null, scope.organizationId(), null,
                java.util.Map.of("absenceWeeks", weeks, "slaHigh", high, "slaNormal", normal, "slaLow", low)));
        return get(scope.organizationId());
    }

    /** [V1] Cada regla debe ser un entero ≥ 1. */
    private static int pos(Integer v, int def) {
        if (v == null) {
            return def;
        }
        if (v < 1) {
            throw new Exceptions("error.pastoral.ruleInvalid", HttpStatus.BAD_REQUEST);
        }
        return v;
    }
}
