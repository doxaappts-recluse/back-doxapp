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
import pe.dcs.app.shared.export.XlsxWriter;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PaginationResponse;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * M11 · Asignaciones de personas a cargos de un ministerio de la sede, con historial. [V6] verificación vigente si el ministerio la exige · [V7] solo adultos si el ministerio
 * o el cargo lo piden · [V8] una persona no repite el mismo cargo activo en el mismo ministerio · [V9] las fechas no van al futuro y el fin no antecede al inicio.
 * Un cargo de liderazgo fija al líder de la sede; al terminarlo, el líder se libera si la persona no tiene otro cargo de liderazgo.
 */
@Service
@RequiredArgsConstructor
public class MinistryAssignmentService {

    static final String MODULE = MinistrySupport.MODULE;
    private static final String ENTITY = "MinistryAssignment";
    private static final Set<String> STATUSES = Set.of("ACTIVE", "ENDED");

    private final NamedParameterJdbcTemplate jdbc;
    private final MinistrySupport support;
    private final AuthorizationService authz;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Clock clock;

    private static final String SELECT = "select a.id, a.branch_ministry_id, b.ministry_id, m.name as mname, m.color, b.branch_id, br.name as bname, a.person_id,"
            + " trim(p.first_name || ' ' || p.last_name) as pname, p.birth_date, a.position_id, po.name as poname, po.is_leader, a.from_date, a.to_date, a.status, a.end_reason,"
            + " m.requires_screening, m.screening_type"
            + " from ministry_assignment a join branch_ministry b on b.id = a.branch_ministry_id join ministry m on m.id = b.ministry_id join branch br on br.id = b.branch_id"
            + " join person p on p.id = a.person_id join ministry_position po on po.id = a.position_id";

    private MinistryDtos.AssignmentResponse map(java.sql.ResultSet rs, LocalDate today) throws java.sql.SQLException {
        UUID person = (UUID) rs.getObject("person_id");
        String status = rs.getString("status");
        LocalDate birth = rs.getDate("birth_date") == null ? null : rs.getDate("birth_date").toLocalDate();
        String screening = "ACTIVE".equals(status) ? support.screeningState(person, rs.getBoolean("requires_screening"), rs.getString("screening_type"), today)
                : "NOT_REQUIRED";
        return new MinistryDtos.AssignmentResponse((UUID) rs.getObject("id"), (UUID) rs.getObject("branch_ministry_id"), (UUID) rs.getObject("ministry_id"), rs.getString("mname"),
                rs.getString("color"), (UUID) rs.getObject("branch_id"), rs.getString("bname"), person, rs.getString("pname"), (UUID) rs.getObject("position_id"), rs.getString("poname"),
                rs.getBoolean("is_leader"), rs.getDate("from_date").toLocalDate(), rs.getDate("to_date") == null ? null : rs.getDate("to_date").toLocalDate(), status,
                rs.getString("end_reason"), screening, birth != null && java.time.Period.between(birth, today).getYears() < MinistrySupport.ADULT_AGE);
    }

    private MinistryDtos.AssignmentResponse getRaw(UUID id, UUID orgId) {
        LocalDate today = support.orgToday(orgId);
        return jdbc.query(SELECT + " where a.id = :id", new MapSqlParameterSource("id", id), (rs, i) -> map(rs, today)).stream().findFirst()
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    // ---------------------------------------------------------------- consulta

    private String filters(AccessScope scope, MinistryDtos.AssignmentSearch.Filters f, MapSqlParameterSource ps) {
        StringBuilder w = new StringBuilder(MinistrySupport.visibleBm(scope, ps, "b"));
        if (f.branchMinistryId() != null) {
            w.append(" and a.branch_ministry_id = :fbm");
            ps.addValue("fbm", f.branchMinistryId());
        }
        if (f.ministryId() != null) {
            w.append(" and b.ministry_id = :fm");
            ps.addValue("fm", f.ministryId());
        }
        if (f.branchId() != null) {
            w.append(" and b.branch_id = :fb");
            ps.addValue("fb", f.branchId());
        }
        if (f.personId() != null) {
            w.append(" and a.person_id = :fp");
            ps.addValue("fp", f.personId());
        }
        if (MinistrySupport.hasText(f.status())) {
            String st = f.status().trim().toUpperCase();
            if (!STATUSES.contains(st)) {
                throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "estado");
            }
            w.append(" and a.status = :st");
            ps.addValue("st", st);
        }
        if (MinistrySupport.hasText(f.q())) {
            ps.addValue("q", "%" + f.q().trim().toLowerCase().replace("%", "").replace("_", "") + "%");
            w.append(" and lower(p.first_name || ' ' || p.last_name) like :q");
        }
        return w.toString();
    }

    private static MinistryDtos.AssignmentSearch.Filters nf(MinistryDtos.AssignmentSearch req) {
        return req == null || req.filters() == null ? new MinistryDtos.AssignmentSearch.Filters(null, null, null, null, null, null) : req.filters();
    }

    @Transactional(readOnly = true)
    public PageResponse<MinistryDtos.AssignmentResponse> search(AccessScope scope, MinistryDtos.AssignmentSearch req) {
        MapSqlParameterSource ps = new MapSqlParameterSource();
        String w = filters(scope, nf(req), ps);
        Long total = jdbc.queryForObject("select count(*) from ministry_assignment a join branch_ministry b on b.id = a.branch_ministry_id join person p on p.id = a.person_id where " + w, ps, Long.class);
        int size = req == null || req.pagination() == null ? 50 : Math.max(1, Math.min(req.pagination().getSize(), 200));
        int page = req == null || req.pagination() == null ? 0 : Math.max(0, req.pagination().getPage());
        ps.addValue("lim", size).addValue("off", (long) page * size);
        LocalDate today = support.orgToday(scope.organizationId());
        List<MinistryDtos.AssignmentResponse> rows = jdbc.query(SELECT + " where " + w + " order by (a.status = 'ACTIVE') desc, po.is_leader desc, lower(p.last_name), lower(p.first_name), a.from_date desc limit :lim offset :off",
                ps, (rs, i) -> map(rs, today));
        long t = total == null ? 0 : total;
        return new PageResponse<>(rows, new PaginationResponse((int) t, (int) Math.ceil(t / (double) size), size, page));
    }

    /** Historial ministerial de una persona (dentro del alcance de quien consulta). */
    @Transactional(readOnly = true)
    public List<MinistryDtos.PersonMinistry> ofPerson(AccessScope scope, UUID personId) {
        MapSqlParameterSource ps = new MapSqlParameterSource("p", personId);
        String vis = MinistrySupport.visibleBm(scope, ps, "b");
        return jdbc.query("select a.id, a.branch_ministry_id, m.name, br.name, po.name, po.is_leader, a.from_date, a.to_date, a.status from ministry_assignment a"
                        + " join branch_ministry b on b.id = a.branch_ministry_id join ministry m on m.id = b.ministry_id join branch br on br.id = b.branch_id join ministry_position po on po.id = a.position_id"
                        + " where a.person_id = :p and " + vis + " order by (a.status = 'ACTIVE') desc, a.from_date desc", ps,
                (rs, i) -> new MinistryDtos.PersonMinistry((UUID) rs.getObject(1), (UUID) rs.getObject(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getBoolean(6),
                        rs.getDate(7).toLocalDate(), rs.getDate(8) == null ? null : rs.getDate(8).toLocalDate(), rs.getString(9)));
    }

    // ---------------------------------------------------------------- asignar

    @Transactional
    public MinistryDtos.AssignmentResponse assign(AuthenticatedActor actor, AccessScope scope, MinistryDtos.AssignRequest r) {
        authz.require(actor, MODULE, Action.C);
        if (r == null || r.branchMinistryId() == null || r.positionId() == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "ministerio y cargo");
        }
        MinistrySupport.BranchMinistryRow bm = support.lockBm(scope, r.branchMinistryId());
        MinistrySupport.PersonRef p = support.activePerson(scope, r.personId());
        UUID id = insert(actor.ownerId(), scope, bm, p, r.positionId(), r.from());
        return getRaw(id, bm.orgId());
    }

    /** Alta con todas las reglas; la usa también la aprobación de una solicitud de ingreso (con {@code scope} nulo se omiten las restricciones OWN). */
    UUID insert(UUID by, AccessScope scope, MinistrySupport.BranchMinistryRow bm, MinistrySupport.PersonRef p, UUID positionId, LocalDate fromParam) {
        if (!bm.open()) {
            throw new Exceptions("error.ministry.notActive", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        List<Boolean> pos = jdbc.query("select is_leader from ministry_position where id = :id and ministry_id = :m", new MapSqlParameterSource("id", positionId).addValue("m", bm.ministryId()),
                (rs, i) -> rs.getBoolean(1));
        if (pos.isEmpty()) {
            throw new Exceptions("error.ministry.positionInvalid", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        boolean leaderRole = pos.get(0);
        if (leaderRole && scope != null && MinistrySupport.isOwn(scope)) {
            throw new Exceptions("error.ministry.ownLeader", HttpStatus.FORBIDDEN);
        }
        LocalDate today = support.orgToday(bm.orgId());
        LocalDate from = fromParam == null ? today : fromParam;
        if (from.isAfter(today)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "desde");                                             // [V9]
        }
        support.assertEligible(bm, p, leaderRole, today);                                                                             // [V6][V7]
        Integer dup = jdbc.queryForObject("select count(*) from ministry_assignment where branch_ministry_id = :b and person_id = :p and position_id = :po and status = 'ACTIVE'",
                new MapSqlParameterSource("b", bm.id()).addValue("p", p.id()).addValue("po", positionId), Integer.class);
        if (dup != null && dup > 0) {
            throw new Exceptions("error.ministry.assignmentExists", HttpStatus.CONFLICT);                                              // [V8]
        }
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("insert into ministry_assignment (id, organization_id, branch_ministry_id, person_id, position_id, from_date, status, created_at, created_by)"
                            + " values (:id, :o, :b, :p, :po, :f, 'ACTIVE', :at, :by)",
                    new MapSqlParameterSource("id", id).addValue("o", bm.orgId()).addValue("b", bm.id()).addValue("p", p.id()).addValue("po", positionId)
                            .addValue("f", java.sql.Date.valueOf(from)).addValue("at", Timestamp.from(clock.instant())).addValue("by", by));
        } catch (DataIntegrityViolationException e) {
            throw new Exceptions("error.ministry.assignmentExists", HttpStatus.CONFLICT);
        }
        if (leaderRole) {
            jdbc.update("update branch_ministry set leader_person_id = :p, version = version + 1 where id = :b", new MapSqlParameterSource("p", p.id()).addValue("b", bm.id()));
        }
        audit.record(new AuditService.Command(MODULE, "ASSIGN", ENTITY, id, bm.orgId(), bm.branchId(), Map.of("person", p.id().toString(), "position", positionId.toString(), "ministry", bm.ministryName())));
        if (!p.id().equals(by)) {
            notifications.toPersons(leaderRole ? NotificationType.MINISTRY_LEADER_ASSIGNED : NotificationType.MINISTRY_ASSIGNED, bm.orgId(), List.of(p.id()),
                    Map.of("ministry", bm.ministryName(), "branch", support.branchName(bm.branchId())), "/app/ministries", null);
        }
        return id;
    }

    // ---------------------------------------------------------------- terminar

    @Transactional
    public MinistryDtos.AssignmentResponse end(AuthenticatedActor actor, AccessScope scope, UUID id, MinistryDtos.EndRequest r) {
        authz.require(actor, MODULE, Action.E);
        MapSqlParameterSource ps = new MapSqlParameterSource("id", id);
        String vis = MinistrySupport.visibleBm(scope, ps, "b");
        List<Object[]> rows = jdbc.query("select a.branch_ministry_id, a.person_id, a.from_date, a.status, po.is_leader from ministry_assignment a join branch_ministry b on b.id = a.branch_ministry_id"
                        + " join ministry_position po on po.id = a.position_id where a.id = :id and " + vis, ps,
                (rs, i) -> new Object[]{rs.getObject(1), rs.getObject(2), rs.getDate(3).toLocalDate(), rs.getString(4), rs.getBoolean(5)});
        if (rows.isEmpty()) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        Object[] a = rows.get(0);
        MinistrySupport.BranchMinistryRow bm = support.lockBm(scope, (UUID) a[0]);
        if (!"ACTIVE".equals(a[3])) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, a[3]);
        }
        boolean leaderRole = (Boolean) a[4];
        if (leaderRole && MinistrySupport.isOwn(scope)) {
            throw new Exceptions("error.ministry.ownLeader", HttpStatus.FORBIDDEN);
        }
        LocalDate today = support.orgToday(bm.orgId());
        LocalDate to = r == null || r.to() == null ? today : r.to();
        if (to.isAfter(today) || to.isBefore((LocalDate) a[2])) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "hasta");                                             // [V9]
        }
        String reason = MinistrySupport.trim(r == null ? null : r.reason(), 200, "motivo");
        endRow(id, to, reason);
        releaseLeader(bm.id(), (UUID) a[1]);
        audit.record(new AuditService.Command(MODULE, "END", ENTITY, id, bm.orgId(), bm.branchId(), Map.of("person", a[1].toString(), "to", to.toString())));
        return getRaw(id, bm.orgId());
    }

    /** Cierra una asignación (la usan el fin manual, el traslado de sede y la fusión). */
    void endRow(UUID id, LocalDate to, String reason) {
        jdbc.update("update ministry_assignment set status = 'ENDED', to_date = greatest(:t, from_date), end_reason = :r where id = :id and status = 'ACTIVE'",
                new MapSqlParameterSource("t", java.sql.Date.valueOf(to)).addValue("r", reason).addValue("id", id));
    }

    /** Si la persona era el líder de la sede y ya no conserva otro cargo de liderazgo activo, la sede queda sin líder. */
    void releaseLeader(UUID branchMinistryId, UUID personId) {
        jdbc.update("update branch_ministry b set leader_person_id = null, version = version + 1 where b.id = :b and b.leader_person_id = :p and not exists ("
                        + "select 1 from ministry_assignment a join ministry_position po on po.id = a.position_id where a.branch_ministry_id = b.id and a.person_id = :p and a.status = 'ACTIVE' and po.is_leader)",
                new MapSqlParameterSource("b", branchMinistryId).addValue("p", personId));
    }

    // ---------------------------------------------------------------- exportar

    public record ExportResult(byte[] content, int rows) {
    }

    @Transactional
    public ExportResult export(AuthenticatedActor actor, AccessScope scope, MinistryDtos.AssignmentSearch req) {
        authz.require(actor, MODULE, Action.X);
        MapSqlParameterSource ps = new MapSqlParameterSource();
        String w = filters(scope, nf(req), ps);
        LocalDate today = support.orgToday(scope.organizationId());
        List<MinistryDtos.AssignmentResponse> rows = jdbc.query(SELECT + " where " + w + " order by lower(m.name), lower(br.name), lower(p.last_name), lower(p.first_name) limit 5000", ps, (rs, i) -> map(rs, today));
        List<List<Object>> data = new ArrayList<>();
        for (MinistryDtos.AssignmentResponse a : rows) {
            data.add(java.util.Arrays.asList(a.ministryName(), a.branchName(), a.personName(), a.positionName(), a.leader() ? "Sí" : "No", a.from().toString(),
                    a.to() == null ? null : a.to().toString(), a.status(), a.screening(), a.endReason()));
        }
        byte[] bytes = XlsxWriter.write("Ministerios", List.of("Ministerio", "Sede", "Persona", "Cargo", "Liderazgo", "Desde", "Hasta", "Estado", "Verificación", "Motivo de cierre"), data);
        audit.record(new AuditService.Command(MODULE, "EXPORT", ENTITY, scope.organizationId(), scope.organizationId(), null, Map.of("rows", rows.size())));
        return new ExportResult(bytes, rows.size());
    }
}
