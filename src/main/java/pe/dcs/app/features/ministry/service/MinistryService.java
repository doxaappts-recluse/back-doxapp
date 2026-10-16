package pe.dcs.app.features.ministry.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.ministry.dto.MinistryDtos;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.AuthorizationService;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PaginationResponse;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * M11 · Estructura ministerial de la organización (N2) con sus cargos. [V1] nombre único por organización · [V2] cargo único por ministerio · [V3] un ministerio con historial
 * de asignaciones o de sedes no se elimina: se inactiva. Solo el administrador de la organización cambia la estructura; quien lidera un ministerio (OWN) solo ve el suyo.
 */
@Service
@RequiredArgsConstructor
public class MinistryService {

    static final String MODULE = MinistrySupport.MODULE;
    private static final String ENTITY = "Ministry";
    private static final Pattern COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "INACTIVE");

    private final NamedParameterJdbcTemplate jdbc;
    private final MinistrySupport support;
    private final AuthorizationService authz;
    private final AuditService audit;
    private final Clock clock;

    private static final String SELECT = "select m.*, p.name as parent_name,"
            + " (select count(*) from branch_ministry b where b.ministry_id = m.id and b.status = 'ACTIVE') as branches,"
            + " (select count(*) from ministry_assignment a join branch_ministry b on b.id = a.branch_ministry_id where b.ministry_id = m.id and a.status = 'ACTIVE') as assignments"
            + " from ministry m left join ministry p on p.id = m.parent_id";

    /** Ministerios que ve quien consulta: todos los de la organización, salvo quien lidera (OWN), que ve solo los suyos. */
    private String visible(AccessScope scope, MapSqlParameterSource ps, String a) {
        ps.addValue("org", scope.organizationId());
        StringBuilder sb = new StringBuilder(a + ".organization_id = :org");
        if (MinistrySupport.isOwn(scope)) {
            ps.addValue("meOwn", scope.personId() == null ? new UUID(0, 0) : scope.personId());
            sb.append(" and exists (select 1 from branch_ministry ob where ob.ministry_id = ").append(a).append(".id and ob.leader_person_id = :meOwn)");
        }
        return sb.toString();
    }

    private List<MinistryDtos.PositionResponse> positions(UUID ministryId) {
        return jdbc.query("select p.id, p.name, p.is_leader, p.sort_order, (select count(*) from ministry_assignment a where a.position_id = p.id and a.status = 'ACTIVE')"
                + " from ministry_position p where p.ministry_id = :m order by p.sort_order, lower(p.name)", new MapSqlParameterSource("m", ministryId),
                (rs, i) -> new MinistryDtos.PositionResponse((UUID) rs.getObject(1), rs.getString(2), rs.getBoolean(3), rs.getInt(4), rs.getInt(5)));
    }

    private MinistryDtos.MinistryResponse map(java.sql.ResultSet rs) throws java.sql.SQLException {
        UUID id = (UUID) rs.getObject("id");
        return new MinistryDtos.MinistryResponse(id, rs.getString("name"), rs.getString("description"), rs.getString("color"), (UUID) rs.getObject("parent_id"), rs.getString("parent_name"),
                rs.getBoolean("requires_screening"), rs.getString("screening_type"), rs.getBoolean("adult_only"), rs.getString("status"), positions(id), rs.getInt("branches"),
                rs.getInt("assignments"), rs.getTimestamp("created_at").toInstant(), rs.getLong("version"));
    }

    // ---------------------------------------------------------------- consulta

    @Transactional(readOnly = true)
    public PageResponse<MinistryDtos.MinistryResponse> search(AccessScope scope, MinistryDtos.MinistrySearch req) {
        MinistryDtos.MinistrySearch.Filters f = req == null || req.filters() == null ? new MinistryDtos.MinistrySearch.Filters(null, null, null) : req.filters();
        MapSqlParameterSource ps = new MapSqlParameterSource();
        StringBuilder w = new StringBuilder(visible(scope, ps, "m"));
        if (MinistrySupport.hasText(f.q())) {
            ps.addValue("q", "%" + f.q().trim().toLowerCase().replace("%", "").replace("_", "") + "%");
            w.append(" and lower(m.name) like :q");
        }
        if (MinistrySupport.hasText(f.status())) {
            String st = f.status().trim().toUpperCase();
            if (!STATUSES.contains(st)) {
                throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "estado");
            }
            w.append(" and m.status = :st");
            ps.addValue("st", st);
        }
        if (f.parentId() != null) {
            w.append(" and m.parent_id = :pid");
            ps.addValue("pid", f.parentId());
        }
        Long total = jdbc.queryForObject("select count(*) from ministry m where " + w, ps, Long.class);
        int size = req == null || req.pagination() == null ? 50 : Math.max(1, Math.min(req.pagination().getSize(), 200));
        int page = req == null || req.pagination() == null ? 0 : Math.max(0, req.pagination().getPage());
        ps.addValue("lim", size).addValue("off", (long) page * size);
        List<MinistryDtos.MinistryResponse> rows = jdbc.query(SELECT + " where " + w + " order by (m.status = 'ACTIVE') desc, lower(m.name) limit :lim offset :off", ps, (rs, i) -> map(rs));
        long t = total == null ? 0 : total;
        return new PageResponse<>(rows, new PaginationResponse((int) t, (int) Math.ceil(t / (double) size), size, page));
    }

    @Transactional(readOnly = true)
    public MinistryDtos.MinistryResponse get(AccessScope scope, UUID id) {
        MapSqlParameterSource ps = new MapSqlParameterSource("id", id);
        String w = visible(scope, ps, "m");
        return jdbc.query(SELECT + " where m.id = :id and " + w, ps, (rs, i) -> map(rs)).stream().findFirst()
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    private MinistryDtos.MinistryResponse getRaw(UUID id) {
        return jdbc.query(SELECT + " where m.id = :id", new MapSqlParameterSource("id", id), (rs, i) -> map(rs)).stream().findFirst()
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    private void load(AccessScope scope, UUID id) {
        Integer n = jdbc.queryForObject("select count(*) from ministry where id = :id and organization_id = :o", new MapSqlParameterSource("id", id).addValue("o", scope.organizationId()), Integer.class);
        if (n == null || n == 0) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
    }

    // ---------------------------------------------------------------- alta y edición

    private record Fields(String name, String description, String color, UUID parent, boolean requires, String screeningType, boolean adult) {
    }

    private Fields fields(AccessScope scope, UUID selfId, String name, String description, String color, UUID parentId, Boolean requires, String screeningType, Boolean adult) {
        String n = name == null ? "" : name.trim();
        if (n.length() < 2 || n.length() > 80) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "nombre");
        }
        String c = MinistrySupport.hasText(color) ? color.trim() : null;
        if (c != null && !COLOR.matcher(c).matches()) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "color");
        }
        if (parentId != null) {
            if (parentId.equals(selfId)) {
                throw new Exceptions("error.ministry.parentInvalid", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            load(scope, parentId);
            // sin ciclos: el padre no puede descender del propio ministerio
            UUID cur = parentId;
            for (int i = 0; i < 20 && cur != null && selfId != null; i++) {
                if (cur.equals(selfId)) {
                    throw new Exceptions("error.ministry.parentInvalid", HttpStatus.UNPROCESSABLE_ENTITY);
                }
                List<UUID> up = jdbc.queryForList("select parent_id from ministry where id = :id and parent_id is not null", new MapSqlParameterSource("id", cur), UUID.class);
                cur = up.isEmpty() ? null : up.get(0);
            }
        }
        boolean req = Boolean.TRUE.equals(requires);
        String st = MinistrySupport.hasText(screeningType) ? screeningType.trim().toUpperCase() : null;
        if (st != null && !support.catalogExists(scope.organizationId(), "SCREENING_TYPE", st)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "tipo de verificación");
        }
        return new Fields(n, MinistrySupport.trim(description, 500, "descripción"), c, parentId, req, req ? st : null, Boolean.TRUE.equals(adult));
    }

    @Transactional
    public MinistryDtos.MinistryResponse create(AuthenticatedActor actor, AccessScope scope, MinistryDtos.MinistryRequest r) {
        authz.require(actor, MODULE, Action.C);
        MinistrySupport.requireOrgAdmin(scope);
        if (r == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "datos");
        }
        Fields f = fields(scope, null, r.name(), r.description(), r.color(), r.parentId(), r.requiresScreening(), r.screeningType(), r.adultOnly());
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("insert into ministry (id, organization_id, name, description, color, parent_id, requires_screening, screening_type, adult_only, status, created_at, created_by)"
                            + " values (:id, :o, :n, :d, :c, :p, :rs, :st, :ao, 'ACTIVE', :at, :by)",
                    new MapSqlParameterSource("id", id).addValue("o", scope.organizationId()).addValue("n", f.name()).addValue("d", f.description()).addValue("c", f.color())
                            .addValue("p", f.parent()).addValue("rs", f.requires()).addValue("st", f.screeningType()).addValue("ao", f.adult())
                            .addValue("at", Timestamp.from(clock.instant())).addValue("by", actor.ownerId()));
        } catch (DataIntegrityViolationException e) {
            throw new Exceptions("error.ministry.nameTaken", HttpStatus.CONFLICT);
        }
        if (r.positions() != null) {
            int order = 0;
            for (MinistryDtos.PositionRequest p : r.positions()) {
                insertPosition(id, p, order += 10);
            }
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("name", f.name());
        d.put("requiresScreening", f.requires());
        audit.record(new AuditService.Command(MODULE, "CREATE", ENTITY, id, scope.organizationId(), null, d));
        return getRaw(id);
    }

    @Transactional
    public MinistryDtos.MinistryResponse update(AuthenticatedActor actor, AccessScope scope, UUID id, MinistryDtos.MinistryUpdate r) {
        authz.require(actor, MODULE, Action.E);
        MinistrySupport.requireOrgAdmin(scope);
        load(scope, id);
        if (r == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "datos");
        }
        Fields f = fields(scope, id, r.name(), r.description(), r.color(), r.parentId(), r.requiresScreening(), r.screeningType(), r.adultOnly());
        int n;
        try {
            n = jdbc.update("update ministry set name = :n, description = :d, color = :c, parent_id = :p, requires_screening = :rs, screening_type = :st, adult_only = :ao, updated_at = :at,"
                            + " updated_by = :by, version = version + 1 where id = :id and (cast(:v as bigint) is null or version = :v)",
                    new MapSqlParameterSource("id", id).addValue("n", f.name()).addValue("d", f.description()).addValue("c", f.color()).addValue("p", f.parent())
                            .addValue("rs", f.requires()).addValue("st", f.screeningType()).addValue("ao", f.adult()).addValue("at", Timestamp.from(clock.instant()))
                            .addValue("by", actor.ownerId()).addValue("v", r.version()));
        } catch (DataIntegrityViolationException e) {
            throw new Exceptions("error.ministry.nameTaken", HttpStatus.CONFLICT);
        }
        if (n == 0) {
            throw new Exceptions("error.common.concurrentUpdate", HttpStatus.CONFLICT);
        }
        audit.record(new AuditService.Command(MODULE, "UPDATE", ENTITY, id, scope.organizationId(), null, Map.of("name", f.name())));
        return getRaw(id);
    }

    @Transactional
    public MinistryDtos.MinistryResponse setStatus(AuthenticatedActor actor, AccessScope scope, UUID id, boolean active) {
        authz.require(actor, MODULE, Action.S);
        MinistrySupport.requireOrgAdmin(scope);
        load(scope, id);
        jdbc.update("update ministry set status = :s, updated_at = :at, updated_by = :by, version = version + 1 where id = :id",
                new MapSqlParameterSource("s", active ? "ACTIVE" : "INACTIVE").addValue("at", Timestamp.from(clock.instant())).addValue("by", actor.ownerId()).addValue("id", id));
        audit.record(new AuditService.Command(MODULE, active ? "ACTIVATE" : "INACTIVATE", ENTITY, id, scope.organizationId(), null, Map.of("id", id.toString())));
        return getRaw(id);
    }

    /** [V3] Solo se elimina un ministerio que nunca se activó en una sede; con asignaciones (activas o históricas) se inactiva. */
    @Transactional
    public void delete(AuthenticatedActor actor, AccessScope scope, UUID id) {
        authz.require(actor, MODULE, Action.D);
        MinistrySupport.requireOrgAdmin(scope);
        load(scope, id);
        MapSqlParameterSource ps = new MapSqlParameterSource("id", id);
        Integer used = jdbc.queryForObject("select (select count(*) from branch_ministry where ministry_id = :id) + (select count(*) from ministry where parent_id = :id)", ps, Integer.class);
        if (used != null && used > 0) {
            throw new Exceptions("error.ministry.hasAssignments", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        jdbc.update("delete from ministry_position where ministry_id = :id", ps);
        jdbc.update("delete from ministry where id = :id", ps);
        audit.record(new AuditService.Command(MODULE, "DELETE", ENTITY, id, scope.organizationId(), null, Map.of("id", id.toString())));
    }

    // ---------------------------------------------------------------- cargos

    private void insertPosition(UUID ministryId, MinistryDtos.PositionRequest p, int defaultOrder) {
        String name = p == null || p.name() == null ? "" : p.name().trim();
        if (name.length() < 2 || name.length() > 60) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "cargo");
        }
        try {
            jdbc.update("insert into ministry_position (id, ministry_id, name, is_leader, sort_order, created_at) values (:id, :m, :n, :l, :o, :at)",
                    new MapSqlParameterSource("id", UUID.randomUUID()).addValue("m", ministryId).addValue("n", name).addValue("l", Boolean.TRUE.equals(p.leader()))
                            .addValue("o", p.sortOrder() == null ? defaultOrder : p.sortOrder()).addValue("at", Timestamp.from(clock.instant())));
        } catch (DataIntegrityViolationException e) {
            throw new Exceptions("error.ministry.positionTaken", HttpStatus.CONFLICT);
        }
    }

    @Transactional
    public MinistryDtos.MinistryResponse addPosition(AuthenticatedActor actor, AccessScope scope, UUID ministryId, MinistryDtos.PositionRequest r) {
        authz.require(actor, MODULE, Action.E);
        MinistrySupport.requireOrgAdmin(scope);
        load(scope, ministryId);
        Integer max = jdbc.queryForObject("select coalesce(max(sort_order), 0) from ministry_position where ministry_id = :m", new MapSqlParameterSource("m", ministryId), Integer.class);
        insertPosition(ministryId, r, (max == null ? 0 : max) + 10);
        audit.record(new AuditService.Command(MODULE, "POSITION_ADD", ENTITY, ministryId, scope.organizationId(), null, Map.of("position", String.valueOf(r.name()))));
        return getRaw(ministryId);
    }

    @Transactional
    public MinistryDtos.MinistryResponse updatePosition(AuthenticatedActor actor, AccessScope scope, UUID ministryId, UUID positionId, MinistryDtos.PositionRequest r) {
        authz.require(actor, MODULE, Action.E);
        MinistrySupport.requireOrgAdmin(scope);
        load(scope, ministryId);
        String name = r == null || r.name() == null ? "" : r.name().trim();
        if (name.length() < 2 || name.length() > 60) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "cargo");
        }
        int n;
        try {
            n = jdbc.update("update ministry_position set name = :n, is_leader = :l, sort_order = coalesce(:o, sort_order) where id = :id and ministry_id = :m",
                    new MapSqlParameterSource("n", name).addValue("l", Boolean.TRUE.equals(r.leader())).addValue("o", r.sortOrder()).addValue("id", positionId).addValue("m", ministryId));
        } catch (DataIntegrityViolationException e) {
            throw new Exceptions("error.ministry.positionTaken", HttpStatus.CONFLICT);
        }
        if (n == 0) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        audit.record(new AuditService.Command(MODULE, "POSITION_UPDATE", ENTITY, ministryId, scope.organizationId(), null, Map.of("position", name)));
        return getRaw(ministryId);
    }

    /** Un cargo que ya tuvo asignaciones (activas o históricas) no se elimina. */
    @Transactional
    public MinistryDtos.MinistryResponse deletePosition(AuthenticatedActor actor, AccessScope scope, UUID ministryId, UUID positionId) {
        authz.require(actor, MODULE, Action.E);
        MinistrySupport.requireOrgAdmin(scope);
        load(scope, ministryId);
        MapSqlParameterSource ps = new MapSqlParameterSource("id", positionId).addValue("m", ministryId);
        Integer used = jdbc.queryForObject("select count(*) from ministry_assignment where position_id = :id", ps, Integer.class);
        if (used != null && used > 0) {
            throw new Exceptions("error.ministry.positionInUse", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (jdbc.update("delete from ministry_position where id = :id and ministry_id = :m", ps) == 0) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        audit.record(new AuditService.Command(MODULE, "POSITION_DELETE", ENTITY, ministryId, scope.organizationId(), null, Map.of("position", positionId.toString())));
        return getRaw(ministryId);
    }

    // ---------------------------------------------------------------- catálogo para selectores

    /** Cargos de un ministerio de la organización (para los formularios de asignación). */
    @Transactional(readOnly = true)
    public List<MinistryDtos.PositionResponse> positionsOf(AccessScope scope, UUID ministryId) {
        load(scope, ministryId);
        return new ArrayList<>(positions(ministryId));
    }
}
