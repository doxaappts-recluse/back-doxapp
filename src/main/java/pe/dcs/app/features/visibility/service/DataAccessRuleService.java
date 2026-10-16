package pe.dcs.app.features.visibility.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.visibility.dto.VisibilityDtos;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.security.authz.ContractGate;
import pe.dcs.app.security.authz.ModuleCatalog;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.util.Exceptions;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * M21 · Regla de visibilidad de datos entre sedes por módulo. Alcances: CURRENT_BRANCH (por defecto: cada sede ve lo suyo),
 * PERSON_HISTORY (también quienes pasaron por su sede), ORGANIZATION (toda la organización) y APPROVAL_REQUIRED (solo con autorización
 * de la sede dueña, ver {@code VisibilityGrantService}). El efecto es inmediato: los módulos consultan la tabla en cada petición.
 */
@Service
@RequiredArgsConstructor
public class DataAccessRuleService {

    public static final String DEFAULT_SCOPE = "CURRENT_BRANCH";
    public static final Set<String> SCOPES = Set.of("CURRENT_BRANCH", "PERSON_HISTORY", "ORGANIZATION", "APPROVAL_REQUIRED");

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final AuditService audit;
    private final ModuleCatalog catalog;
    private final ContractGate contractGate;
    private final List<VisibilityAwareModule> modules;

    /** Un renglón por módulo que aplica reglas y que la organización tiene disponible. */
    @Transactional(readOnly = true)
    public List<VisibilityDtos.Rule> list(AccessScope scope) {
        Map<String, Object[]> saved = new LinkedHashMap<>();
        jdbc.query("select module_code, scope, enabled, coalesce(updated_at, created_at) from data_access_rule where organization_id = :o",
                new MapSqlParameterSource("o", scope.organizationId()), rs -> {
                    saved.put(rs.getString(1), new Object[]{rs.getString(2), rs.getBoolean(3), rs.getTimestamp(4).toInstant()});
                });
        List<VisibilityDtos.Rule> out = new ArrayList<>();
        for (VisibilityAwareModule m : modules) {
            if (!available(scope.organizationId(), m.moduleCode())) {
                continue;
            }
            Object[] s = saved.get(m.moduleCode());
            out.add(new VisibilityDtos.Rule(m.moduleCode(), s == null ? DEFAULT_SCOPE : (String) s[0], s == null || (boolean) s[1], s != null,
                    m.supportedScopes().stream().sorted().toList(), s == null ? null : (java.time.Instant) s[2]));
        }
        return out;
    }

    @Transactional
    public VisibilityDtos.Rule save(AuthenticatedActor actor, AccessScope scope, String moduleCode, VisibilityDtos.RuleRequest r) {
        VisibilityAwareModule m = modules.stream().filter(x -> x.moduleCode().equals(moduleCode)).findFirst()
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
        if (!available(scope.organizationId(), moduleCode)) {
            throw new Exceptions("error.common.moduleNotContracted", HttpStatus.FORBIDDEN);
        }
        String sc = r == null || r.scope() == null ? "" : r.scope().trim().toUpperCase();
        if (!SCOPES.contains(sc) || !m.supportedScopes().contains(sc)) {
            throw new Exceptions("error.rule.invalidScope", HttpStatus.UNPROCESSABLE_ENTITY);                       // [V1]
        }
        boolean enabled = r.enabled() == null || r.enabled();
        String before = null;
        Boolean beforeEnabled = null;
        List<Object[]> cur = jdbc.query("select scope, enabled from data_access_rule where organization_id = :o and module_code = :m for update",
                new MapSqlParameterSource("o", scope.organizationId()).addValue("m", moduleCode), (rs, i) -> new Object[]{rs.getString(1), rs.getBoolean(2)});
        if (!cur.isEmpty()) {
            before = (String) cur.get(0)[0];
            beforeEnabled = (Boolean) cur.get(0)[1];
        }
        Timestamp now = Timestamp.from(clock.instant());
        MapSqlParameterSource ps = new MapSqlParameterSource("o", scope.organizationId()).addValue("m", moduleCode).addValue("s", sc).addValue("e", enabled)
                .addValue("now", now).addValue("by", scope.personId());
        if (cur.isEmpty()) {
            jdbc.update("insert into data_access_rule (id, organization_id, module_code, scope, enabled, created_at, created_by, version) values (:id, :o, :m, :s, :e, :now, :by, 0)",
                    ps.addValue("id", UUID.randomUUID()));
        } else {
            jdbc.update("update data_access_rule set scope = :s, enabled = :e, updated_at = :now, updated_by = :by, version = version + 1 where organization_id = :o and module_code = :m", ps);
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("module", moduleCode);
        d.put("from", before == null ? DEFAULT_SCOPE : before);
        d.put("to", sc);
        d.put("enabledFrom", beforeEnabled == null ? Boolean.TRUE : beforeEnabled);
        d.put("enabledTo", enabled);
        audit.record(new AuditService.Command("VISIBILITY_RULES", "RULE_UPDATE", "DataAccessRule", moduleCode, scope.organizationId(), null, d));
        return list(scope).stream().filter(x -> x.moduleCode().equals(moduleCode)).findFirst().orElseThrow();
    }

    /** Alcance vigente de un módulo: el configurado si está habilitado; si no, la sede propia. */
    @Transactional(readOnly = true)
    public String effectiveScope(UUID orgId, String moduleCode) {
        List<String> s = jdbc.queryForList("select scope from data_access_rule where organization_id = :o and module_code = :m and enabled",
                new MapSqlParameterSource("o", orgId).addValue("m", moduleCode), String.class);
        return s.isEmpty() ? DEFAULT_SCOPE : s.get(0);
    }

    public boolean isAware(String moduleCode) {
        return modules.stream().anyMatch(m -> m.moduleCode().equals(moduleCode));
    }

    private boolean available(UUID orgId, String moduleCode) {
        var m = catalog.find(moduleCode);
        if (m == null || !m.isPublished()) {
            return false;
        }
        return m.getKind() != pe.dcs.app.features.module.domain.ModuleKind.CONTRACTABLE || contractGate.enabled(orgId, moduleCode);
    }
}
