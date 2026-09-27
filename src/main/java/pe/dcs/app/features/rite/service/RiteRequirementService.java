package pe.dcs.app.features.rite.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.rite.dto.RiteDtos;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.AuthorizationService;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.RoleType;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * M08 · Requisitos de membresía y de los ritos por organización [V10] y la lista de verificación de cada solicitud.
 * Fuentes: MANUAL (alguien marca que se cumple) y AGE (el sistema lo calcula con la fecha de nacimiento). COURSE llega con M13.
 */
@Service
@RequiredArgsConstructor
public class RiteRequirementService {

    private static final Set<String> TYPES = Set.of("MEMBERSHIP", "BAPTISM", "MARRIAGE", "DEDICATION");
    private static final Set<String> SOURCES = Set.of("MANUAL", "AGE");
    private static final Pattern CODE = Pattern.compile("[A-Z0-9_]{2,30}");

    private final NamedParameterJdbcTemplate jdbc;
    private final AuthorizationService authz;
    private final RiteSupport support;
    private final AuditService audit;
    private final Clock clock;

    // ---------------------------------------------------------------- configuración

    @Transactional(readOnly = true)
    public List<RiteDtos.RequirementResponse> list(AuthenticatedActor actor, AccessScope scope, String riteType, boolean onlyActive) {
        String type = type(riteType);
        authz.require(actor, RiteSupport.moduleOf(type), Action.V);
        return jdbc.query("select id, rite_type, code, label, required, source, min_age, active, sort_order, version from rite_requirement"
                        + " where organization_id = :o and rite_type = :t" + (onlyActive ? " and active" : "") + " order by sort_order, lower(label)",
                new MapSqlParameterSource("o", scope.organizationId()).addValue("t", type), (rs, i) -> map(rs));
    }

    @Transactional
    public RiteDtos.RequirementResponse create(AuthenticatedActor actor, AccessScope scope, RiteDtos.RequirementRequest r) {
        onlyAdmin(scope);
        String type = type(r == null ? null : r.riteType());
        authz.require(actor, RiteSupport.moduleOf(type), Action.E);
        Fields f = fields(r);
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("insert into rite_requirement (id, organization_id, rite_type, code, label, required, source, min_age, active, sort_order, created_at, created_by)"
                            + " values (:id, :o, :t, :c, :l, :r, :s, :a, :act, :so, :at, :by)",
                    new MapSqlParameterSource("id", id).addValue("o", scope.organizationId()).addValue("t", type).addValue("c", f.code).addValue("l", f.label)
                            .addValue("r", f.required).addValue("s", f.source).addValue("a", f.minAge).addValue("act", f.active).addValue("so", f.sort)
                            .addValue("at", Timestamp.from(clock.instant())).addValue("by", actor.ownerId()));
        } catch (DuplicateKeyException e) {
            throw new Exceptions("error.rite.codeExists", HttpStatus.CONFLICT, f.code);
        }
        audit.record(new AuditService.Command(RiteSupport.moduleOf(type), "REQUIREMENT_CREATE", "RiteRequirement", id, scope.organizationId(), null, diff(type, f)));
        return one(scope.organizationId(), id);
    }

    @Transactional
    public RiteDtos.RequirementResponse update(AuthenticatedActor actor, AccessScope scope, UUID id, RiteDtos.RequirementRequest r) {
        onlyAdmin(scope);
        RiteDtos.RequirementResponse cur = one(scope.organizationId(), id);
        authz.require(actor, RiteSupport.moduleOf(cur.riteType()), Action.E);
        Fields f = fields(r);
        int n = jdbc.update("update rite_requirement set label = :l, required = :r, source = :s, min_age = :a, active = :act, sort_order = :so, updated_at = :at, updated_by = :by,"
                        + " version = version + 1 where id = :id and organization_id = :o and (:v is null or version = :v)",
                new MapSqlParameterSource("id", id).addValue("o", scope.organizationId()).addValue("l", f.label).addValue("r", f.required).addValue("s", f.source)
                        .addValue("a", f.minAge).addValue("act", f.active).addValue("so", f.sort).addValue("v", r.version())
                        .addValue("at", Timestamp.from(clock.instant())).addValue("by", actor.ownerId()));
        if (n == 0) {
            throw new Exceptions("error.common.concurrentUpdate", HttpStatus.CONFLICT);
        }
        audit.record(new AuditService.Command(RiteSupport.moduleOf(cur.riteType()), "REQUIREMENT_UPDATE", "RiteRequirement", id, scope.organizationId(), null, diff(cur.riteType(), f)));
        return one(scope.organizationId(), id);
    }

    /** Solo se elimina un requisito que nunca se usó; si ya tiene marcas se desactiva. */
    @Transactional
    public void delete(AuthenticatedActor actor, AccessScope scope, UUID id) {
        onlyAdmin(scope);
        RiteDtos.RequirementResponse cur = one(scope.organizationId(), id);
        authz.require(actor, RiteSupport.moduleOf(cur.riteType()), Action.E);
        Integer used = jdbc.queryForObject("select count(*) from rite_requirement_check where requirement_id = :id", new MapSqlParameterSource("id", id), Integer.class);
        if (used != null && used > 0) {
            throw new Exceptions("error.rite.requirementInUse", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        jdbc.update("delete from rite_requirement where id = :id", new MapSqlParameterSource("id", id));
        audit.record(new AuditService.Command(RiteSupport.moduleOf(cur.riteType()), "REQUIREMENT_DELETE", "RiteRequirement", id, scope.organizationId(), null,
                Map.of("riteType", cur.riteType(), "label", cur.label())));
    }

    // ---------------------------------------------------------------- lista de verificación

    /** Evalúa los requisitos activos de un tipo para un sujeto (membresía o rito). Las edades se calculan con las fechas de nacimiento de las personas del sujeto. */
    @Transactional(readOnly = true)
    public RiteDtos.Checklist checklist(UUID orgId, String type, UUID subjectId, List<LocalDate> birthDates, LocalDate today, String overrideReason) {
        MapSqlParameterSource ps = new MapSqlParameterSource("o", orgId).addValue("t", type).addValue("s", subjectId);
        List<RiteDtos.CheckItem> items = jdbc.query("select q.id, q.code, q.label, q.required, q.source, q.min_age, c.met, c.note from rite_requirement q"
                        + " left join rite_requirement_check c on c.requirement_id = q.id and c.subject_id = :s"
                        + " where q.organization_id = :o and q.rite_type = :t and q.active order by q.sort_order, lower(q.label)", ps, (rs, i) -> {
            String source = rs.getString(5);
            Integer minAge = (Integer) rs.getObject(6);
            boolean auto = "AGE".equals(source);
            boolean met;
            String detail = null;
            if (auto) {
                met = !birthDates.isEmpty();
                for (LocalDate b : birthDates) {
                    Integer age = RiteSupport.age(b, today);
                    if (age == null) {
                        met = false;
                        detail = "birthDate";
                    } else if (age < minAge) {
                        met = false;
                        detail = "age";
                    }
                }
            } else {
                Boolean m = (Boolean) rs.getObject(7);
                met = Boolean.TRUE.equals(m);
            }
            return new RiteDtos.CheckItem((UUID) rs.getObject(1), rs.getString(2), rs.getString(3), rs.getBoolean(4), source, minAge, met, auto, rs.getString(8), detail);
        });
        boolean complete = items.stream().noneMatch(x -> x.required() && !x.met());
        return new RiteDtos.Checklist(items, complete, RiteSupport.hasText(overrideReason), overrideReason);
    }

    /** Marca (o desmarca) un requisito MANUAL para un sujeto. */
    @Transactional
    public void mark(AuthenticatedActor actor, AccessScope scope, String type, UUID subjectId, UUID requirementId, RiteDtos.CheckRequest r) {
        if (r == null || r.met() == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "cumple");
        }
        RiteDtos.RequirementResponse q = one(scope.organizationId(), requirementId);
        if (!q.riteType().equals(type) || !q.active()) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        if (!"MANUAL".equals(q.source())) {
            throw new Exceptions("error.rite.requirementAuto", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        String note = RiteSupport.trim(r.note(), 200);
        jdbc.update("insert into rite_requirement_check (id, requirement_id, subject_id, met, note, checked_by, checked_at) values (:id, :q, :s, :m, :n, :by, :at)"
                        + " on conflict (subject_id, requirement_id) do update set met = excluded.met, note = excluded.note, checked_by = excluded.checked_by, checked_at = excluded.checked_at",
                new MapSqlParameterSource("id", UUID.randomUUID()).addValue("q", requirementId).addValue("s", subjectId).addValue("m", r.met()).addValue("n", note)
                        .addValue("by", scope.personId()).addValue("at", Timestamp.from(clock.instant())));
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("requirement", q.code());
        d.put("met", r.met());
        audit.record(new AuditService.Command(RiteSupport.moduleOf(type), "REQUIREMENT_CHECK", "RiteRequirementCheck", subjectId, scope.organizationId(), null, d));
    }

    /** Requisitos obligatorios sin cumplir (etiquetas), para mostrarlos en el error. */
    public List<String> unmet(RiteDtos.Checklist c) {
        List<String> out = new ArrayList<>();
        for (RiteDtos.CheckItem i : c.items()) {
            if (i.required() && !i.met()) {
                out.add(i.label());
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- utilidades

    private RiteDtos.RequirementResponse one(UUID orgId, UUID id) {
        return jdbc.query("select id, rite_type, code, label, required, source, min_age, active, sort_order, version from rite_requirement where id = :id and organization_id = :o",
                new MapSqlParameterSource("id", id).addValue("o", orgId), (rs, i) -> map(rs)).stream().findFirst()
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    private static RiteDtos.RequirementResponse map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new RiteDtos.RequirementResponse((UUID) rs.getObject(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBoolean(5), rs.getString(6),
                (Integer) rs.getObject(7), rs.getBoolean(8), rs.getInt(9), rs.getLong(10));
    }

    private static void onlyAdmin(AccessScope scope) {
        if (scope.role() != RoleType.ORG_ADMIN) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
    }

    private static String type(String raw) {
        String t = raw == null ? "" : raw.trim().toUpperCase();
        if (!TYPES.contains(t)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "tipo");
        }
        return t;
    }

    private record Fields(String code, String label, boolean required, String source, Integer minAge, boolean active, int sort) {
    }

    private static Fields fields(RiteDtos.RequirementRequest r) {
        if (r == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "datos");
        }
        String code = r.code() == null ? "" : r.code().trim().toUpperCase().replace(' ', '_');
        if (!CODE.matcher(code).matches()) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "código");
        }
        String label = RiteSupport.trim(r.label(), 120);
        if (label == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "nombre");
        }
        String source = r.source() == null || r.source().isBlank() ? "MANUAL" : r.source().trim().toUpperCase();
        if ("COURSE".equals(source)) {
            throw new Exceptions("error.rite.sourceUnavailable", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (!SOURCES.contains(source)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "fuente");
        }
        Integer minAge = null;
        if ("AGE".equals(source)) {
            minAge = r.minAge();
            if (minAge == null || minAge < 1 || minAge > 120) {
                throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "edad mínima");
            }
        }
        int sort = r.sortOrder() == null ? 0 : Math.max(0, Math.min(r.sortOrder(), 1000));
        return new Fields(code, label, r.required() == null || r.required(), source, minAge, r.active() == null || r.active(), sort);
    }

    private static Map<String, Object> diff(String type, Fields f) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("riteType", type);
        d.put("requirement", f.code);
        d.put("label", f.label);
        d.put("required", f.required);
        d.put("source", f.source);
        d.put("active", f.active);
        return d;
    }
}
