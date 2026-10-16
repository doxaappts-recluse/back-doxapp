package pe.dcs.app.features.ministry.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.ministry.dto.MinistryDtos;
import pe.dcs.app.features.notification.domain.NotificationType;
import pe.dcs.app.features.notification.service.NotificationService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * M11 · Ministerios activos en una sede. [V4] un ministerio se activa una vez por sede y solo si está activo en la estructura · [V5] el líder es adulto y, si el ministerio lo
 * exige, tiene verificación vigente. Quien lidera (OWN) ve y administra su equipo, pero no activa ministerios ni cambia el liderazgo.
 */
@Service
@RequiredArgsConstructor
public class BranchMinistryService {

    static final String MODULE = MinistrySupport.MODULE;
    private static final String ENTITY = "BranchMinistry";
    private static final Set<String> STATUSES = Set.of("ACTIVE", "INACTIVE");

    private final NamedParameterJdbcTemplate jdbc;
    private final MinistrySupport support;
    private final AuthorizationService authz;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Clock clock;

    private static final String SELECT = "select b.id, b.ministry_id, m.name, m.color, m.requires_screening, m.adult_only, b.branch_id, br.name as branch_name, b.leader_person_id,"
            + " trim(l.first_name || ' ' || l.last_name) as leader_name, b.status, b.version,"
            + " (select count(*) from ministry_assignment a where a.branch_ministry_id = b.id and a.status = 'ACTIVE') as team,"
            + " (select count(*) from approval_request r where r.type = 'MINISTRY_JOIN' and r.subject_id = b.id and r.status = 'PENDING') as pending"
            + " from branch_ministry b join ministry m on m.id = b.ministry_id join branch br on br.id = b.branch_id left join person l on l.id = b.leader_person_id";

    private static MinistryDtos.BranchMinistryResponse map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new MinistryDtos.BranchMinistryResponse((UUID) rs.getObject("id"), (UUID) rs.getObject("ministry_id"), rs.getString("name"), rs.getString("color"),
                rs.getBoolean("requires_screening"), rs.getBoolean("adult_only"), (UUID) rs.getObject("branch_id"), rs.getString("branch_name"), (UUID) rs.getObject("leader_person_id"),
                rs.getString("leader_name"), rs.getString("status"), rs.getInt("team"), rs.getInt("pending"), rs.getLong("version"));
    }

    @Transactional(readOnly = true)
    public PageResponse<MinistryDtos.BranchMinistryResponse> search(AccessScope scope, MinistryDtos.BranchMinistrySearch req) {
        MinistryDtos.BranchMinistrySearch.Filters f = req == null || req.filters() == null ? new MinistryDtos.BranchMinistrySearch.Filters(null, null, null, null) : req.filters();
        MapSqlParameterSource ps = new MapSqlParameterSource();
        StringBuilder w = new StringBuilder(MinistrySupport.visibleBm(scope, ps, "b"));
        if (MinistrySupport.hasText(f.q())) {
            ps.addValue("q", "%" + f.q().trim().toLowerCase().replace("%", "").replace("_", "") + "%");
            w.append(" and lower(m.name) like :q");
        }
        if (f.branchId() != null) {
            w.append(" and b.branch_id = :fb");
            ps.addValue("fb", f.branchId());
        }
        if (f.ministryId() != null) {
            w.append(" and b.ministry_id = :fm");
            ps.addValue("fm", f.ministryId());
        }
        if (MinistrySupport.hasText(f.status())) {
            String st = f.status().trim().toUpperCase();
            if (!STATUSES.contains(st)) {
                throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "estado");
            }
            w.append(" and b.status = :st");
            ps.addValue("st", st);
        }
        Long total = jdbc.queryForObject("select count(*) from branch_ministry b join ministry m on m.id = b.ministry_id where " + w, ps, Long.class);
        int size = req == null || req.pagination() == null ? 50 : Math.max(1, Math.min(req.pagination().getSize(), 200));
        int page = req == null || req.pagination() == null ? 0 : Math.max(0, req.pagination().getPage());
        ps.addValue("lim", size).addValue("off", (long) page * size);
        List<MinistryDtos.BranchMinistryResponse> rows = jdbc.query(SELECT + " where " + w + " order by (b.status = 'ACTIVE') desc, lower(br.name), lower(m.name) limit :lim offset :off", ps, (rs, i) -> map(rs));
        long t = total == null ? 0 : total;
        return new PageResponse<>(rows, new PaginationResponse((int) t, (int) Math.ceil(t / (double) size), size, page));
    }

    @Transactional(readOnly = true)
    public MinistryDtos.BranchMinistryResponse get(AccessScope scope, UUID id) {
        support.loadBm(scope, id);
        return getRaw(id);
    }

    MinistryDtos.BranchMinistryResponse getRaw(UUID id) {
        return jdbc.query(SELECT + " where b.id = :id", new MapSqlParameterSource("id", id), (rs, i) -> map(rs)).stream().findFirst()
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    private static void notOwn(AccessScope scope) {
        if (MinistrySupport.isOwn(scope)) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
    }

    // ---------------------------------------------------------------- activar en una sede

    @Transactional
    public MinistryDtos.BranchMinistryResponse create(AuthenticatedActor actor, AccessScope scope, MinistryDtos.BranchMinistryRequest r) {
        authz.require(actor, MODULE, Action.C);
        notOwn(scope);
        if (r == null || r.ministryId() == null || r.branchId() == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "ministerio y sede");
        }
        List<String> ms = jdbc.queryForList("select status from ministry where id = :id and organization_id = :o", new MapSqlParameterSource("id", r.ministryId()).addValue("o", scope.organizationId()), String.class);
        if (ms.isEmpty()) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        if (!"ACTIVE".equals(ms.get(0))) {
            throw new Exceptions("error.ministry.notActive", HttpStatus.UNPROCESSABLE_ENTITY);                                          // [V4]
        }
        List<String> bs = jdbc.queryForList("select status from branch where id = :id and organization_id = :o", new MapSqlParameterSource("id", r.branchId()).addValue("o", scope.organizationId()), String.class);
        if (bs.isEmpty() || !scope.canSeeBranch(r.branchId())) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        if (!"ACTIVE".equals(bs.get(0))) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, bs.get(0));
        }
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("insert into branch_ministry (id, organization_id, ministry_id, branch_id, status, created_at, created_by) values (:id, :o, :m, :b, 'ACTIVE', :at, :by)",
                    new MapSqlParameterSource("id", id).addValue("o", scope.organizationId()).addValue("m", r.ministryId()).addValue("b", r.branchId())
                            .addValue("at", Timestamp.from(clock.instant())).addValue("by", actor.ownerId()));
        } catch (DataIntegrityViolationException e) {
            throw new Exceptions("error.ministry.branchMinistryExists", HttpStatus.CONFLICT);
        }
        audit.record(new AuditService.Command(MODULE, "BRANCH_ACTIVATE", ENTITY, id, scope.organizationId(), r.branchId(), Map.of("ministry", r.ministryId().toString())));
        if (r.leaderPersonId() != null) {
            applyLeader(actor, scope, support.loadBmRaw(id), r.leaderPersonId());
        }
        return getRaw(id);
    }

    // ---------------------------------------------------------------- liderazgo

    @Transactional
    public MinistryDtos.BranchMinistryResponse setLeader(AuthenticatedActor actor, AccessScope scope, UUID id, MinistryDtos.LeaderRequest r) {
        authz.require(actor, MODULE, Action.E);
        notOwn(scope);
        MinistrySupport.BranchMinistryRow bm = support.lockBm(scope, id);
        applyLeader(actor, scope, bm, r == null ? null : r.leaderPersonId());
        return getRaw(id);
    }

    private void applyLeader(AuthenticatedActor actor, AccessScope scope, MinistrySupport.BranchMinistryRow bm, UUID personId) {
        Map<String, Object> diff = new LinkedHashMap<>();
        if (personId == null) {
            jdbc.update("update branch_ministry set leader_person_id = null, updated_at = :at, updated_by = :by, version = version + 1 where id = :id",
                    new MapSqlParameterSource("at", Timestamp.from(clock.instant())).addValue("by", actor.ownerId()).addValue("id", bm.id()));
            diff.put("leader", null);
        } else {
            MinistrySupport.PersonRef p = support.activePerson(scope, personId);
            support.assertEligible(bm, p, true, support.orgToday(bm.orgId()));                                                          // [V5]
            jdbc.update("update branch_ministry set leader_person_id = :p, updated_at = :at, updated_by = :by, version = version + 1 where id = :id",
                    new MapSqlParameterSource("p", personId).addValue("at", Timestamp.from(clock.instant())).addValue("by", actor.ownerId()).addValue("id", bm.id()));
            diff.put("leader", personId.toString());
            if (!personId.equals(actor.ownerId())) {
                notifications.toPersons(NotificationType.MINISTRY_LEADER_ASSIGNED, bm.orgId(), List.of(personId),
                        Map.of("ministry", bm.ministryName(), "branch", support.branchName(bm.branchId())), "/app/ministries", null);
            }
        }
        audit.record(new AuditService.Command(MODULE, "LEADER", ENTITY, bm.id(), bm.orgId(), bm.branchId(), diff));
    }

    // ---------------------------------------------------------------- estado

    /** Inactivar un ministerio en la sede no termina las asignaciones: quedan como historial visible hasta que se cierren. */
    @Transactional
    public MinistryDtos.BranchMinistryResponse setStatus(AuthenticatedActor actor, AccessScope scope, UUID id, boolean active) {
        authz.require(actor, MODULE, Action.S);
        notOwn(scope);
        MinistrySupport.BranchMinistryRow bm = support.lockBm(scope, id);
        if (active && !"ACTIVE".equals(bm.ministryStatus())) {
            throw new Exceptions("error.ministry.notActive", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        jdbc.update("update branch_ministry set status = :s, updated_at = :at, updated_by = :by, version = version + 1 where id = :id",
                new MapSqlParameterSource("s", active ? "ACTIVE" : "INACTIVE").addValue("at", Timestamp.from(clock.instant())).addValue("by", actor.ownerId()).addValue("id", id));
        audit.record(new AuditService.Command(MODULE, active ? "BRANCH_RESUME" : "BRANCH_PAUSE", ENTITY, id, bm.orgId(), bm.branchId(), Map.of("id", id.toString())));
        return getRaw(id);
    }
}
