package pe.dcs.app.features.group.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.group.dto.GroupDtos;
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

/** M10 · Reglas por organización [V1]: líder con membresía (sí), varios grupos por persona (sí) y mínimo de líderes adultos con menores (2, entero ≥ 1). Solo el administrador de la organización las edita. */
@Service
@RequiredArgsConstructor
public class GroupRulesService {

    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService audit;
    private final Clock clock;

    @Transactional(readOnly = true)
    public GroupDtos.Rules get(UUID orgId) {
        return jdbc.query("select leader_requires_membership, allow_multiple_groups, min_adult_leaders_minors, version from group_rules where organization_id = :o",
                new MapSqlParameterSource("o", orgId), (rs, i) -> new GroupDtos.Rules(rs.getBoolean(1), rs.getBoolean(2), rs.getInt(3), rs.getLong(4))).stream().findFirst()
                .orElse(new GroupDtos.Rules(true, true, 2, null));
    }

    @Transactional
    public GroupDtos.Rules update(AuthenticatedActor actor, AccessScope scope, GroupDtos.RulesRequest r) {
        if (scope.role() != RoleType.ORG_ADMIN) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
        if (r == null || r.minAdultLeadersMinors() == null || r.minAdultLeadersMinors() < 1 || r.minAdultLeadersMinors() > 10) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "líderes adultos mínimos");
        }
        GroupDtos.Rules cur = get(scope.organizationId());
        boolean lrm = r.leaderRequiresMembership() == null ? cur.leaderRequiresMembership() : r.leaderRequiresMembership();
        boolean amg = r.allowMultipleGroups() == null ? cur.allowMultipleGroups() : r.allowMultipleGroups();
        MapSqlParameterSource ps = new MapSqlParameterSource("o", scope.organizationId()).addValue("l", lrm).addValue("a", amg).addValue("m", r.minAdultLeadersMinors())
                .addValue("at", Timestamp.from(clock.instant())).addValue("by", actor.ownerId());
        if (cur.version() == null) {
            jdbc.update("insert into group_rules (organization_id, leader_requires_membership, allow_multiple_groups, min_adult_leaders_minors, updated_at, updated_by)"
                    + " values (:o, :l, :a, :m, :at, :by)", ps);
        } else {
            if (r.version() != null && !r.version().equals(cur.version())) {
                throw new Exceptions("error.common.concurrentUpdate", HttpStatus.CONFLICT);
            }
            jdbc.update("update group_rules set leader_requires_membership = :l, allow_multiple_groups = :a, min_adult_leaders_minors = :m, updated_at = :at, updated_by = :by,"
                    + " version = version + 1 where organization_id = :o", ps);
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("leaderRequiresMembership", lrm);
        d.put("allowMultipleGroups", amg);
        d.put("minAdultLeadersMinors", r.minAdultLeadersMinors());
        audit.record(new AuditService.Command(GroupSupport.MODULE, "RULES", "GroupRules", scope.organizationId(), scope.organizationId(), null, d));
        return get(scope.organizationId());
    }
}
