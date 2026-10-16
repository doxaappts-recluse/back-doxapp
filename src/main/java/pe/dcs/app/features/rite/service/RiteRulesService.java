package pe.dcs.app.features.rite.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.rite.dto.RiteDtos;
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

/** M08 · Reglas por organización: edad mínima para casarse (16–30) y edad máxima para la presentación de niños (1–17). Solo las edita el administrador de la organización. */
@Service
@RequiredArgsConstructor
public class RiteRulesService {

    static final int DEFAULT_MARRIAGE_MIN_AGE = 18;
    static final int DEFAULT_DEDICATION_MAX_AGE = 12;

    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService audit;
    private final Clock clock;

    @Transactional(readOnly = true)
    public RiteDtos.Rules get(UUID orgId) {
        return jdbc.query("select marriage_min_age, dedication_max_age, version from rite_rules where organization_id = :o", new MapSqlParameterSource("o", orgId),
                (rs, i) -> new RiteDtos.Rules(rs.getInt(1), rs.getInt(2), rs.getLong(3))).stream().findFirst()
                .orElse(new RiteDtos.Rules(DEFAULT_MARRIAGE_MIN_AGE, DEFAULT_DEDICATION_MAX_AGE, null));
    }

    @Transactional
    public RiteDtos.Rules update(AuthenticatedActor actor, AccessScope scope, RiteDtos.RulesRequest r) {
        if (scope.role() != RoleType.ORG_ADMIN) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
        if (r == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "datos");
        }
        int marriage = range(r.marriageMinAge(), 16, 30, "edad mínima para casarse");
        int dedication = range(r.dedicationMaxAge(), 1, 17, "edad máxima de presentación");
        RiteDtos.Rules cur = get(scope.organizationId());
        MapSqlParameterSource ps = new MapSqlParameterSource("o", scope.organizationId()).addValue("m", marriage).addValue("d", dedication)
                .addValue("at", Timestamp.from(clock.instant())).addValue("by", actor.ownerId());
        if (cur.version() == null) {
            jdbc.update("insert into rite_rules (organization_id, marriage_min_age, dedication_max_age, updated_at, updated_by) values (:o, :m, :d, :at, :by)", ps);
        } else {
            if (r.version() != null && !r.version().equals(cur.version())) {
                throw new Exceptions("error.common.concurrentUpdate", HttpStatus.CONFLICT);
            }
            jdbc.update("update rite_rules set marriage_min_age = :m, dedication_max_age = :d, updated_at = :at, updated_by = :by, version = version + 1 where organization_id = :o", ps);
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("marriageMinAge", marriage);
        d.put("dedicationMaxAge", dedication);
        audit.record(new AuditService.Command("MARRIAGE", "RULES", "RiteRules", scope.organizationId(), scope.organizationId(), null, d));
        return get(scope.organizationId());
    }

    private static int range(Integer v, int min, int max, String label) {
        if (v == null || v < min || v > max) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, label);
        }
        return v;
    }
}
