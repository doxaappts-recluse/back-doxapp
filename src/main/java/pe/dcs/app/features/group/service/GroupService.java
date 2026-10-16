package pe.dcs.app.features.group.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.approval.service.ApprovalEngine;
import pe.dcs.app.features.group.dto.GroupDtos;
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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * M10 · Grupos y células. Ciclo DRAFT → ACTIVE ⇄ PAUSED → CLOSED. [V2] nombre 2–80 único por sede · [V3] activar exige líder activo de la sede (y membresía vigente si la
 * organización lo pide) · [V4] menores: líderes adultos suficientes y ningún líder menor · [V5] cupo ≥ 1 y ≥ integrantes · [V6] integrante único activo; varios grupos según la regla ·
 * [V7] un menor solo entra a grupos MINOR/YOUTH/MIXED · [V9] multiplicar mueve integrantes a un grupo hijo con otro líder · [V10] cerrar exige motivo.
 * Quien es ORG_USER solo opera los grupos que lidera (OWN) y no cambia al líder.
 */
@Service
@RequiredArgsConstructor
public class GroupService {

    static final String MODULE = GroupSupport.MODULE;
    private static final String ENTITY = "SmallGroup";
    private static final Set<String> STATUSES = Set.of("DRAFT", "ACTIVE", "PAUSED", "CLOSED");

    private final NamedParameterJdbcTemplate jdbc;
    private final GroupSupport support;
    private final GroupRulesService rules;
    private final AuthorizationService authz;
    private final ApprovalEngine engine;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Clock clock;

    private static final String SELECT = "select g.*, b.name as branch_name, trim(h.first_name || ' ' || h.last_name) as host_name, pg.name as parent_name,"
            + " (select count(*) from group_member m where m.group_id = g.id and m.status = 'ACTIVE') as members,"
            + " (select m.person_id from group_member m where m.group_id = g.id and m.status = 'ACTIVE' and m.role = 'LEADER') as leader_id,"
            + " (select trim(p.first_name || ' ' || p.last_name) from group_member m join person p on p.id = m.person_id where m.group_id = g.id and m.status = 'ACTIVE' and m.role = 'LEADER') as leader_name,"
            + " (select string_agg(trim(p.first_name || ' ' || p.last_name), '|' order by p.last_name, p.first_name) from group_member m join person p on p.id = m.person_id"
            + "   where m.group_id = g.id and m.status = 'ACTIVE' and m.role = 'COLEADER') as coleaders,"
            + " (select min(t.meeting_date) from group_meeting t where t.group_id = g.id and t.status = 'PLANNED' and t.meeting_date >= current_date - 1) as next_meeting,"
            + " (select count(*) from approval_request a where a.type = 'GROUP_JOIN' and a.subject_id = g.id and a.status = 'PENDING') as pending,"
            + " exists (select 1 from group_member m join person p on p.id = m.person_id where m.group_id = g.id and m.status = 'ACTIVE' and p.birth_date is not null"
            + "   and p.birth_date > current_date - interval '18 years') as has_minors"
            + " from small_group g join branch b on b.id = g.branch_id left join person h on h.id = g.host_person_id left join small_group pg on pg.id = g.parent_group_id";

    private static LocalDate date(ResultSet rs, String col) throws SQLException {
        java.sql.Date d = rs.getDate(col);
        return d == null ? null : d.toLocalDate();
    }

    private static GroupDtos.GroupResponse map(ResultSet rs) throws SQLException {
        Time mt = rs.getTime("meeting_time");
        int day = rs.getInt("meeting_day");
        Integer meetingDay = rs.wasNull() ? null : day;
        int cap = rs.getInt("capacity");
        Integer capacity = rs.wasNull() ? null : cap;
        String co = rs.getString("coleaders");
        return new GroupDtos.GroupResponse((UUID) rs.getObject("id"), (UUID) rs.getObject("branch_id"), rs.getString("branch_name"), rs.getString("name"), rs.getString("category"),
                rs.getString("audience"), rs.getString("description"), (UUID) rs.getObject("host_person_id"), rs.getString("host_name"), meetingDay, mt == null ? null : mt.toLocalTime(),
                rs.getString("location"), rs.getString("zone"), capacity, rs.getBoolean("open_to_join"), date(rs, "start_date"), date(rs, "end_date"), (UUID) rs.getObject("parent_group_id"),
                rs.getString("parent_name"), rs.getString("status"), rs.getString("status_reason"), rs.getInt("members"), (UUID) rs.getObject("leader_id"), rs.getString("leader_name"),
                co == null ? List.of() : List.of(co.split("\\|")), date(rs, "next_meeting"), rs.getInt("pending"), rs.getBoolean("has_minors"), rs.getTimestamp("created_at").toInstant(),
                rs.getLong("version"));
    }

    // ---------------------------------------------------------------- consulta

    @Transactional(readOnly = true)
    public PageResponse<GroupDtos.GroupResponse> search(AccessScope scope, GroupDtos.Search req) {
        GroupDtos.Search.Filters f = req == null || req.filters() == null ? new GroupDtos.Search.Filters(null, null, null, null, null, null) : req.filters();
        MapSqlParameterSource ps = new MapSqlParameterSource();
        StringBuilder w = new StringBuilder(GroupSupport.visible(scope, ps, "g"));
        filters(f, w, ps);
        Long total = jdbc.queryForObject("select count(*) from small_group g where " + w, ps, Long.class);
        int size = req == null || req.pagination() == null ? 20 : Math.max(1, Math.min(req.pagination().getSize(), 200));
        int page = req == null || req.pagination() == null ? 0 : Math.max(0, req.pagination().getPage());
        ps.addValue("lim", size).addValue("off", (long) page * size);
        List<GroupDtos.GroupResponse> rows = jdbc.query(SELECT + " where " + w + " order by case g.status when 'ACTIVE' then 0 when 'PAUSED' then 1 when 'DRAFT' then 2 else 3 end,"
                + " lower(g.name) limit :lim offset :off", ps, (rs, i) -> map(rs));
        long t = total == null ? 0 : total;
        return new PageResponse<>(rows, new PaginationResponse((int) t, (int) Math.ceil(t / (double) size), size, page));
    }

    private void filters(GroupDtos.Search.Filters f, StringBuilder w, MapSqlParameterSource ps) {
        if (GroupSupport.hasText(f.q())) {
            ps.addValue("q", "%" + f.q().trim().toLowerCase().replace("%", "").replace("_", "") + "%");
            w.append(" and (lower(g.name) like :q or lower(coalesce(g.zone, '')) like :q or exists (select 1 from group_member m join person p on p.id = m.person_id"
                    + " where m.group_id = g.id and m.status = 'ACTIVE' and m.role = 'LEADER' and lower(p.first_name || ' ' || p.last_name) like :q))");
        }
        if (f.branchId() != null) {
            w.append(" and g.branch_id = :fb");
            ps.addValue("fb", f.branchId());
        }
        if (GroupSupport.hasText(f.status())) {
            String st = f.status().trim().toUpperCase();
            if (!STATUSES.contains(st)) {
                throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "estado");
            }
            w.append(" and g.status = :st");
            ps.addValue("st", st);
        }
        if (GroupSupport.hasText(f.category())) {
            w.append(" and g.category = :cat");
            ps.addValue("cat", f.category().trim().toUpperCase());
        }
        if (GroupSupport.hasText(f.audience())) {
            String a = f.audience().trim().toUpperCase();
            if (!GroupSupport.AUDIENCES.contains(a)) {
                throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "público");
            }
            w.append(" and g.audience = :aud");
            ps.addValue("aud", a);
        }
        if (f.openToJoin() != null) {
            w.append(" and g.open_to_join = :otj");
            ps.addValue("otj", f.openToJoin());
        }
    }

    @Transactional(readOnly = true)
    public GroupDtos.GroupResponse get(AccessScope scope, UUID id) {
        MapSqlParameterSource ps = new MapSqlParameterSource("id", id);
        String w = GroupSupport.visible(scope, ps, "g");
        return jdbc.query(SELECT + " where g.id = :id and " + w, ps, (rs, i) -> map(rs)).stream().findFirst()
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    /** Lectura sin alcance de filas (después de crear un grupo el creador puede no ser su líder). */
    GroupDtos.GroupResponse getRaw(UUID id) {
        return jdbc.query(SELECT + " where g.id = :id", new MapSqlParameterSource("id", id), (rs, i) -> map(rs)).stream().findFirst()
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    /** Grupos en los que participa (o participó) una persona, dentro del alcance de quien consulta. */
    @Transactional(readOnly = true)
    public List<GroupDtos.PersonGroup> ofPerson(AccessScope scope, UUID personId) {
        MapSqlParameterSource ps = new MapSqlParameterSource("p", personId);
        String w = GroupSupport.visible(scope, ps, "g");
        return jdbc.query("select g.id, g.name, b.name as bn, m.role, m.status, m.joined_at, m.left_at from group_member m join small_group g on g.id = m.group_id"
                + " join branch b on b.id = g.branch_id where m.person_id = :p and " + w + " order by (m.status = 'ACTIVE') desc, m.joined_at desc", ps,
                (rs, i) -> new GroupDtos.PersonGroup((UUID) rs.getObject(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), date(rs, "joined_at"), date(rs, "left_at")));
    }

    @Transactional(readOnly = true)
    public List<GroupDtos.MemberResponse> members(AccessScope scope, UUID groupId, boolean includeLeft) {
        GroupSupport.GroupRow g = support.load(scope, groupId);
        LocalDate today = support.today(g.branchId());
        return jdbc.query("select m.id, m.person_id, trim(p.first_name || ' ' || p.last_name) as name, m.role, m.status, m.joined_at, m.left_at, m.left_reason, p.birth_date"
                        + " from group_member m join person p on p.id = m.person_id where m.group_id = :g" + (includeLeft ? "" : " and m.status = 'ACTIVE'")
                        + " order by (m.status = 'ACTIVE') desc, case m.role when 'LEADER' then 0 when 'COLEADER' then 1 when 'INTERN' then 2 else 3 end, lower(p.last_name), lower(p.first_name)",
                new MapSqlParameterSource("g", groupId), (rs, i) -> {
                    LocalDate b = date(rs, "birth_date");
                    return new GroupDtos.MemberResponse((UUID) rs.getObject("id"), (UUID) rs.getObject("person_id"), rs.getString("name"), rs.getString("role"), rs.getString("status"),
                            date(rs, "joined_at"), date(rs, "left_at"), rs.getString("left_reason"), GroupSupport.minor(b, today), GroupSupport.age(b, today), false);
                });
    }

    // ---------------------------------------------------------------- alta y edición

    /** Datos ya validados de un grupo. */
    private record Fields(String name, String category, String audience, String description, UUID host, Integer day, LocalTime time, String location, String zone, Integer capacity,
                          boolean open, LocalDate start, LocalDate end) {
    }

    private Fields fields(AccessScope scope, String name, String category, String audience, String description, UUID host, Integer day, LocalTime time, String location, String zone,
                          Integer capacity, Boolean open, LocalDate start, LocalDate end) {
        String n = name == null ? "" : name.trim();
        if (n.length() < 2 || n.length() > 80) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "nombre");                                             // [V2]
        }
        String cat = GroupSupport.hasText(category) ? category.trim().toUpperCase() : null;
        if (cat != null && !support.categoryExists(scope.organizationId(), cat)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "categoría");
        }
        String aud = GroupSupport.hasText(audience) ? audience.trim().toUpperCase() : "ADULT";
        if (!GroupSupport.AUDIENCES.contains(aud)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "público");
        }
        if (day != null && (day < 1 || day > 7)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "día");
        }
        if (capacity != null && capacity < 1) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "cupo");                                              // [V5]
        }
        if (start != null && end != null && end.isBefore(start)) {
            throw new Exceptions("error.common.dateRange", HttpStatus.BAD_REQUEST);
        }
        if (host != null) {
            support.activeVisible(scope, host);
        }
        return new Fields(n, cat, aud, GroupSupport.trim(description, 500, "descripción"), host, day, time, GroupSupport.trim(location, 160, "ubicación"), GroupSupport.trim(zone, 80, "zona"),
                capacity, open != null && open, start, end);
    }

    private void assertNameFree(UUID branchId, String name, UUID except) {
        Integer n = jdbc.queryForObject("select count(*) from small_group where branch_id = :b and lower(name) = lower(:n) and (cast(:x as uuid) is null or id <> :x)",
                new MapSqlParameterSource("b", branchId).addValue("n", name).addValue("x", except), Integer.class);
        if (n != null && n > 0) {
            throw new Exceptions("error.group.nameTaken", HttpStatus.CONFLICT);
        }
    }

    @Transactional
    public GroupDtos.GroupResponse create(AuthenticatedActor actor, AccessScope scope, GroupDtos.GroupRequest r) {
        authz.require(actor, MODULE, Action.C);
        if (r == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "datos");
        }
        UUID branch = r.branchId() != null ? r.branchId() : actor.activeBranchId();
        if (branch == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "sede");
        }
        if (!scope.canSeeBranch(branch)) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        List<String> bs = jdbc.queryForList("select status from branch where id = :b and organization_id = :o", new MapSqlParameterSource("b", branch).addValue("o", scope.organizationId()), String.class);
        if (bs.isEmpty()) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        if (!"ACTIVE".equals(bs.get(0))) {
            throw new Exceptions("error.group.branchInactive", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        Fields f = fields(scope, r.name(), r.category(), r.audience(), r.description(), r.hostPersonId(), r.meetingDay(), r.meetingTime(), r.location(), r.zone(), r.capacity(),
                r.openToJoin(), r.startDate(), r.endDate());
        assertNameFree(branch, f.name(), null);
        UUID id = UUID.randomUUID();
        LocalDate start = f.start() == null ? support.today(branch) : f.start();
        try {
            jdbc.update("insert into small_group (id, organization_id, branch_id, name, category, audience, description, host_person_id, meeting_day, meeting_time, location, zone,"
                            + " capacity, open_to_join, start_date, end_date, status, created_at, created_by) values (:id, :o, :b, :n, :c, :a, :d, :h, :md, :mt, :l, :z, :cap, :op, :sd, :ed,"
                            + " 'DRAFT', :at, :by)",
                    new MapSqlParameterSource("id", id).addValue("o", scope.organizationId()).addValue("b", branch).addValue("n", f.name()).addValue("c", f.category())
                            .addValue("a", f.audience()).addValue("d", f.description()).addValue("h", f.host()).addValue("md", f.day())
                            .addValue("mt", f.time() == null ? null : Time.valueOf(f.time())).addValue("l", f.location()).addValue("z", f.zone()).addValue("cap", f.capacity())
                            .addValue("op", f.open()).addValue("sd", java.sql.Date.valueOf(start)).addValue("ed", f.end() == null ? null : java.sql.Date.valueOf(f.end()))
                            .addValue("at", Timestamp.from(clock.instant())).addValue("by", actor.ownerId()));
        } catch (DataIntegrityViolationException e) {
            throw new Exceptions("error.group.nameTaken", HttpStatus.CONFLICT);
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("name", f.name());
        d.put("audience", f.audience());
        audit.record(new AuditService.Command(MODULE, "CREATE", ENTITY, id, scope.organizationId(), branch, d));
        if (r.leaderPersonId() != null) {
            GroupSupport.GroupRow g = support.loadRaw(id);
            addChecked(g, support.activeVisible(scope, r.leaderPersonId()), "LEADER", actor);
        }
        return getRaw(id);
    }

    @Transactional
    public GroupDtos.GroupResponse update(AuthenticatedActor actor, AccessScope scope, UUID id, GroupDtos.GroupUpdate r) {
        authz.require(actor, MODULE, Action.E);
        GroupSupport.GroupRow g = support.lock(scope, id);
        if (r == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "datos");
        }
        if ("CLOSED".equals(g.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, g.status());
        }
        Fields f = fields(scope, r.name(), r.category(), r.audience(), r.description(), r.hostPersonId(), r.meetingDay(), r.meetingTime(), r.location(), r.zone(), r.capacity(),
                r.openToJoin(), r.startDate(), r.endDate());
        assertNameFree(g.branchId(), f.name(), id);
        int members = support.activeCount(id);
        if (f.capacity() != null && f.capacity() < members) {
            throw new Exceptions("error.group.capacityInvalid", HttpStatus.UNPROCESSABLE_ENTITY);                                      // [V5]
        }
        if ("ADULT".equals(f.audience()) && hasMinorMembers(g)) {
            throw new Exceptions("error.group.minorNotAllowed", HttpStatus.UNPROCESSABLE_ENTITY);                                      // [V7]
        }
        int n;
        try {
            n = jdbc.update("update small_group set name = :n, category = :c, audience = :a, description = :d, host_person_id = :h, meeting_day = :md, meeting_time = :mt, location = :l,"
                            + " zone = :z, capacity = :cap, open_to_join = :op, start_date = coalesce(:sd, start_date), end_date = :ed, updated_at = :at, updated_by = :by, version = version + 1"
                            + " where id = :id and (cast(:v as bigint) is null or version = :v)",
                    new MapSqlParameterSource("n", f.name()).addValue("c", f.category()).addValue("a", f.audience()).addValue("d", f.description()).addValue("h", f.host())
                            .addValue("md", f.day()).addValue("mt", f.time() == null ? null : Time.valueOf(f.time())).addValue("l", f.location()).addValue("z", f.zone())
                            .addValue("cap", f.capacity()).addValue("op", f.open()).addValue("sd", f.start() == null ? null : java.sql.Date.valueOf(f.start()))
                            .addValue("ed", f.end() == null ? null : java.sql.Date.valueOf(f.end())).addValue("at", Timestamp.from(clock.instant())).addValue("by", actor.ownerId())
                            .addValue("id", id).addValue("v", r.version()));
        } catch (DataIntegrityViolationException e) {
            throw new Exceptions("error.group.nameTaken", HttpStatus.CONFLICT);
        }
        if (n == 0) {
            throw new Exceptions("error.common.concurrentUpdate", HttpStatus.CONFLICT);
        }
        support.assertLeadership(support.loadRaw(id), "ACTIVE".equals(g.status()));
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("name", f.name());
        d.put("audience", f.audience());
        d.put("capacity", f.capacity());
        d.put("openToJoin", f.open());
        audit.record(new AuditService.Command(MODULE, "UPDATE", ENTITY, id, g.orgId(), g.branchId(), d));
        return get(scope, id);
    }

    private boolean hasMinorMembers(GroupSupport.GroupRow g) {
        Integer n = jdbc.queryForObject("select count(*) from group_member m join person p on p.id = m.person_id where m.group_id = :g and m.status = 'ACTIVE' and p.birth_date is not null"
                + " and p.birth_date > :limit", new MapSqlParameterSource("g", g.id()).addValue("limit", java.sql.Date.valueOf(support.today(g.branchId()).minusYears(GroupSupport.ADULT_AGE))), Integer.class);
        return n != null && n > 0;
    }

    // ---------------------------------------------------------------- estado

    @Transactional
    public GroupDtos.GroupResponse activate(AuthenticatedActor actor, AccessScope scope, UUID id) {
        authz.require(actor, MODULE, Action.S);
        GroupSupport.GroupRow g = support.lock(scope, id);
        if (!"DRAFT".equals(g.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, g.status());
        }
        return setStatus(actor, scope, g, "ACTIVE", null, "ACTIVATE");
    }

    @Transactional
    public GroupDtos.GroupResponse pause(AuthenticatedActor actor, AccessScope scope, UUID id, String reason) {
        authz.require(actor, MODULE, Action.S);
        GroupSupport.GroupRow g = support.lock(scope, id);
        if (!"ACTIVE".equals(g.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, g.status());
        }
        return setStatus(actor, scope, g, "PAUSED", GroupSupport.trim(reason, 255, "motivo"), "PAUSE");
    }

    @Transactional
    public GroupDtos.GroupResponse resume(AuthenticatedActor actor, AccessScope scope, UUID id) {
        authz.require(actor, MODULE, Action.S);
        GroupSupport.GroupRow g = support.lock(scope, id);
        if (!"PAUSED".equals(g.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, g.status());
        }
        return setStatus(actor, scope, g, "ACTIVE", null, "RESUME");
    }

    private GroupDtos.GroupResponse setStatus(AuthenticatedActor actor, AccessScope scope, GroupSupport.GroupRow g, String to, String reason, String action) {
        jdbc.update("update small_group set status = :s, status_reason = :r, updated_at = :at, updated_by = :by, version = version + 1 where id = :id",
                new MapSqlParameterSource("s", to).addValue("r", reason).addValue("at", Timestamp.from(clock.instant())).addValue("by", actor.ownerId()).addValue("id", g.id()));
        if ("ACTIVE".equals(to)) {
            support.assertLeadership(support.loadRaw(g.id()), true);                                                                  // [V3][V4]
        }
        audit.record(new AuditService.Command(MODULE, action, ENTITY, g.id(), g.orgId(), g.branchId(), reason == null ? Map.of("name", g.name()) : Map.of("name", g.name(), "reason", reason)));
        return get(scope, g.id());
    }

    /** [V10] Cerrar exige motivo; cancela las reuniones futuras, da de baja a los integrantes y rechaza las solicitudes de ingreso abiertas. */
    @Transactional
    public GroupDtos.GroupResponse close(AuthenticatedActor actor, AccessScope scope, UUID id, String reason) {
        authz.require(actor, MODULE, Action.S);
        GroupSupport.GroupRow g = support.lock(scope, id);
        if ("CLOSED".equals(g.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, g.status());
        }
        String why = reason == null ? "" : reason.trim();
        if (why.length() < 3) {
            throw new Exceptions("error.group.closeReason", HttpStatus.BAD_REQUEST);
        }
        if (why.length() > 255) {
            throw new Exceptions("error.common.tooLong", HttpStatus.BAD_REQUEST, "motivo", 255);
        }
        LocalDate today = support.today(g.branchId());
        MapSqlParameterSource ps = new MapSqlParameterSource("g", id).addValue("t", java.sql.Date.valueOf(today)).addValue("r", why).addValue("at", Timestamp.from(clock.instant()));
        int meetings = jdbc.update("update group_meeting set status = 'CANCELLED', cancel_reason = 'Grupo cerrado', updated_at = :at, version = version + 1"
                + " where group_id = :g and status = 'PLANNED' and meeting_date >= :t", ps);
        int left = jdbc.update("update group_member set status = 'LEFT', left_at = greatest(:t, joined_at), left_reason = 'CLOSED' where group_id = :g and status = 'ACTIVE'", ps);
        for (UUID a : jdbc.queryForList("select id from approval_request where type = 'GROUP_JOIN' and subject_id = :g and status = 'PENDING'", ps, UUID.class)) {
            engine.decideInternal(a, false, scope.personId(), "Grupo cerrado");
        }
        jdbc.update("update small_group set status = 'CLOSED', status_reason = :r, closed_at = :at, updated_at = :at, updated_by = :by, version = version + 1 where id = :g",
                ps.addValue("by", actor.ownerId()));
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("name", g.name());
        d.put("reason", why);
        d.put("meetingsCancelled", meetings);
        d.put("membersLeft", left);
        audit.record(new AuditService.Command(MODULE, "CLOSE", ENTITY, id, g.orgId(), g.branchId(), d));
        return get(scope, id);
    }

    /** Solo se elimina un borrador sin reuniones. */
    @Transactional
    public void delete(AuthenticatedActor actor, AccessScope scope, UUID id) {
        authz.require(actor, MODULE, Action.D);
        GroupSupport.GroupRow g = support.lock(scope, id);
        Integer meetings = jdbc.queryForObject("select count(*) from group_meeting where group_id = :g", new MapSqlParameterSource("g", id), Integer.class);
        Integer children = jdbc.queryForObject("select count(*) from small_group where parent_group_id = :g", new MapSqlParameterSource("g", id), Integer.class);
        if (!"DRAFT".equals(g.status()) || (meetings != null && meetings > 0) || (children != null && children > 0)) {
            throw new Exceptions("error.group.cannotDelete", HttpStatus.CONFLICT);
        }
        jdbc.update("delete from group_member where group_id = :g", new MapSqlParameterSource("g", id));
        jdbc.update("delete from small_group where id = :g", new MapSqlParameterSource("g", id));
        audit.record(new AuditService.Command(MODULE, "DELETE", ENTITY, id, g.orgId(), g.branchId(), Map.of("name", g.name())));
    }

    // ---------------------------------------------------------------- integrantes

    @Transactional
    public GroupDtos.MemberResponse addMember(AuthenticatedActor actor, AccessScope scope, UUID groupId, GroupDtos.MemberAdd r) {
        authz.require(actor, MODULE, Action.E);
        GroupSupport.GroupRow g = support.lock(scope, groupId);
        if (r == null || r.personId() == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "persona");
        }
        String role = role(r.role());
        if (GroupSupport.isOwn(scope) && "LEADER".equals(role)) {
            throw new Exceptions("error.group.ownLeader", HttpStatus.FORBIDDEN);
        }
        UUID mid = addChecked(g, support.activeVisible(scope, r.personId()), role, actor);
        return member(groupId, mid);
    }

    /**
     * Alta de un integrante con todas las reglas [V5][V6][V7][V3][V4]. La usan el alta manual, la multiplicación y la aprobación de una solicitud de ingreso.
     * Si el rol es LEADER, el líder anterior pasa a co-líder.
     */
    UUID addChecked(GroupSupport.GroupRow g, GroupSupport.PersonInfo p, String role, AuthenticatedActor actor) {
        return addChecked(g, p, role, actor == null ? null : actor.ownerId(), actor == null ? null : actor.ownerId());
    }

    UUID addChecked(GroupSupport.GroupRow g, GroupSupport.PersonInfo p, String role, UUID by, UUID notifyExcept) {
        if ("CLOSED".equals(g.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, g.status());
        }
        if (!p.active()) {
            throw new Exceptions("error.group.personInactive", HttpStatus.UNPROCESSABLE_ENTITY, p.name());
        }
        LocalDate today = support.today(g.branchId());
        boolean minor = GroupSupport.minor(p.birthDate(), today);
        if (minor && "ADULT".equals(g.audience())) {
            throw new Exceptions("error.group.minorNotAllowed", HttpStatus.UNPROCESSABLE_ENTITY);                                      // [V7]
        }
        MapSqlParameterSource q = new MapSqlParameterSource("g", g.id()).addValue("p", p.id());
        Integer dup = jdbc.queryForObject("select count(*) from group_member where group_id = :g and person_id = :p and status = 'ACTIVE'", q, Integer.class);
        if (dup != null && dup > 0) {
            throw new Exceptions("error.group.alreadyMember", HttpStatus.CONFLICT);                                                    // [V6]
        }
        if (g.capacity() != null && support.activeCount(g.id()) >= g.capacity()) {
            throw new Exceptions("error.group.full", HttpStatus.UNPROCESSABLE_ENTITY);                                                 // [V5][V11]
        }
        if (!rules.get(g.orgId()).allowMultipleGroups()) {
            Integer other = jdbc.queryForObject("select count(*) from group_member m join small_group x on x.id = m.group_id where m.person_id = :p and m.status = 'ACTIVE'"
                    + " and x.status <> 'CLOSED' and m.group_id <> :g", q, Integer.class);
            if (other != null && other > 0) {
                throw new Exceptions("error.group.multipleNotAllowed", HttpStatus.UNPROCESSABLE_ENTITY);                               // [V6]
            }
        }
        if ("LEADER".equals(role)) {
            jdbc.update("update group_member set role = 'COLEADER' where group_id = :g and role = 'LEADER' and status = 'ACTIVE'", q);
        }
        UUID id = UUID.randomUUID();
        jdbc.update("insert into group_member (id, organization_id, group_id, person_id, role, status, joined_at, created_at, created_by) values (:id, :o, :g, :p, :r, 'ACTIVE', :d, :at, :by)",
                q.addValue("id", id).addValue("o", g.orgId()).addValue("r", role).addValue("d", java.sql.Date.valueOf(today)).addValue("at", Timestamp.from(clock.instant())).addValue("by", by));
        support.assertLeadership(support.loadRaw(g.id()), "ACTIVE".equals(g.status()));
        audit.record(new AuditService.Command(MODULE, "MEMBER_ADD", ENTITY, g.id(), g.orgId(), g.branchId(), Map.of("person", p.id().toString(), "role", role)));
        if ("LEADER".equals(role) && !p.id().equals(notifyExcept)) {
            notifications.toPersons(NotificationType.GROUP_LEADER_ASSIGNED, g.orgId(), List.of(p.id()), Map.of("group", g.name(), "branch", support.branchName(g.branchId())),
                    "/app/groups/" + g.id(), null);
        }
        return id;
    }

    @Transactional
    public GroupDtos.MemberResponse changeRole(AuthenticatedActor actor, AccessScope scope, UUID groupId, UUID memberId, GroupDtos.MemberRole r) {
        authz.require(actor, MODULE, Action.E);
        GroupSupport.GroupRow g = support.lock(scope, groupId);
        if ("CLOSED".equals(g.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, g.status());
        }
        String role = role(r == null ? null : r.role());
        List<Object[]> cur = jdbc.query("select role, person_id from group_member where id = :m and group_id = :g and status = 'ACTIVE'", new MapSqlParameterSource("m", memberId).addValue("g", groupId),
                (rs, i) -> new Object[]{rs.getString(1), rs.getObject(2)});
        if (cur.isEmpty()) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        String old = (String) cur.get(0)[0];
        if (old.equals(role)) {
            return member(groupId, memberId);
        }
        if (GroupSupport.isOwn(scope) && ("LEADER".equals(role) || "LEADER".equals(old))) {
            throw new Exceptions("error.group.ownLeader", HttpStatus.FORBIDDEN);
        }
        if ("LEADER".equals(role)) {
            jdbc.update("update group_member set role = 'COLEADER' where group_id = :g and role = 'LEADER' and status = 'ACTIVE'", new MapSqlParameterSource("g", groupId));
        }
        jdbc.update("update group_member set role = :r where id = :m", new MapSqlParameterSource("r", role).addValue("m", memberId));
        support.assertLeadership(support.loadRaw(groupId), "ACTIVE".equals(g.status()));
        audit.record(new AuditService.Command(MODULE, "MEMBER_ROLE", ENTITY, groupId, g.orgId(), g.branchId(), Map.of("person", String.valueOf(cur.get(0)[1]), "from", old, "to", role)));
        if ("LEADER".equals(role) && !cur.get(0)[1].equals(scope.personId())) {
            notifications.toPersons(NotificationType.GROUP_LEADER_ASSIGNED, g.orgId(), List.of((UUID) cur.get(0)[1]), Map.of("group", g.name(), "branch", support.branchName(g.branchId())),
                    "/app/groups/" + g.id(), null);
        }
        return member(groupId, memberId);
    }

    @Transactional
    public void removeMember(AuthenticatedActor actor, AccessScope scope, UUID groupId, UUID memberId) {
        authz.require(actor, MODULE, Action.E);
        GroupSupport.GroupRow g = support.lock(scope, groupId);
        if ("CLOSED".equals(g.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, g.status());
        }
        List<Object[]> cur = jdbc.query("select role, person_id from group_member where id = :m and group_id = :g and status = 'ACTIVE'", new MapSqlParameterSource("m", memberId).addValue("g", groupId),
                (rs, i) -> new Object[]{rs.getString(1), rs.getObject(2)});
        if (cur.isEmpty()) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        String role = (String) cur.get(0)[0];
        if (GroupSupport.isOwn(scope) && "LEADER".equals(role)) {
            throw new Exceptions("error.group.ownLeader", HttpStatus.FORBIDDEN);
        }
        endMember(memberId, support.today(g.branchId()), "REMOVED");
        support.assertLeadership(support.loadRaw(groupId), "ACTIVE".equals(g.status()));
        audit.record(new AuditService.Command(MODULE, "MEMBER_REMOVE", ENTITY, groupId, g.orgId(), g.branchId(), Map.of("person", String.valueOf(cur.get(0)[1]), "role", role)));
    }

    void endMember(UUID memberId, LocalDate today, String reason) {
        jdbc.update("update group_member set status = 'LEFT', left_at = greatest(:t, joined_at), left_reason = :r where id = :m and status = 'ACTIVE'",
                new MapSqlParameterSource("t", java.sql.Date.valueOf(today)).addValue("r", reason).addValue("m", memberId));
    }

    private static String role(String r) {
        String role = r == null || r.isBlank() ? "MEMBER" : r.trim().toUpperCase();
        if (!GroupSupport.ROLES.contains(role)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "rol");
        }
        return role;
    }

    private GroupDtos.MemberResponse member(UUID groupId, UUID memberId) {
        GroupSupport.GroupRow g = support.loadRaw(groupId);
        LocalDate today = support.today(g.branchId());
        return jdbc.query("select m.id, m.person_id, trim(p.first_name || ' ' || p.last_name) as name, m.role, m.status, m.joined_at, m.left_at, m.left_reason, p.birth_date"
                        + " from group_member m join person p on p.id = m.person_id where m.id = :m", new MapSqlParameterSource("m", memberId), (rs, i) -> {
                    LocalDate b = date(rs, "birth_date");
                    return new GroupDtos.MemberResponse((UUID) rs.getObject("id"), (UUID) rs.getObject("person_id"), rs.getString("name"), rs.getString("role"), rs.getString("status"),
                            date(rs, "joined_at"), date(rs, "left_at"), rs.getString("left_reason"), GroupSupport.minor(b, today), GroupSupport.age(b, today), false);
                }).stream().findFirst().orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    // ---------------------------------------------------------------- multiplicación

    /**
     * [V9] Elige integrantes y un nuevo líder (uno de ellos, distinto del líder del grupo), crea el grupo hijo enlazado con el padre y mueve a los elegidos.
     * El grupo de origen sigue activo con el líder de siempre; ambos deben cumplir las reglas de liderazgo después del movimiento.
     */
    @Transactional
    public GroupDtos.MultiplyResult multiply(AuthenticatedActor actor, AccessScope scope, UUID id, GroupDtos.MultiplyRequest r) {
        authz.require(actor, MODULE, Action.C);
        GroupSupport.GroupRow g = support.lock(scope, id);
        if (!"ACTIVE".equals(g.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, g.status());
        }
        if (r == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "datos");
        }
        Set<UUID> ids = r.memberIds() == null ? Set.of() : new LinkedHashSet<>(r.memberIds());
        UUID leader = r.leaderPersonId();
        UUID co = r.coLeaderPersonId();
        if (ids.isEmpty() || leader == null || !ids.contains(leader) || (co != null && (!ids.contains(co) || co.equals(leader)))) {
            throw new Exceptions("error.group.multiplyInvalid", HttpStatus.UNPROCESSABLE_ENTITY);                                    // [V9]
        }
        List<UUID> currentIds = jdbc.queryForList("select person_id from group_member where group_id = :g and status = 'ACTIVE'", new MapSqlParameterSource("g", id), UUID.class);
        UUID currentLeader = jdbc.queryForList("select person_id from group_member where group_id = :g and status = 'ACTIVE' and role = 'LEADER'", new MapSqlParameterSource("g", id), UUID.class)
                .stream().findFirst().orElse(null);
        if (!currentIds.containsAll(ids) || ids.contains(currentLeader) || leader.equals(currentLeader)) {
            throw new Exceptions("error.group.multiplyInvalid", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        String name = r.name() == null ? "" : r.name().trim();
        if (name.length() < 2 || name.length() > 80) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "nombre");
        }
        assertNameFree(g.branchId(), name, null);
        if (r.meetingDay() != null && (r.meetingDay() < 1 || r.meetingDay() > 7)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "día");
        }
        // el hijo hereda categoría, público y horario del padre salvo lo que se indique
        Map<String, Object> pr = jdbc.queryForMap("select category, audience, meeting_day, meeting_time, location, zone from small_group where id = :g", new MapSqlParameterSource("g", id));
        UUID childId = UUID.randomUUID();
        LocalDate today = support.today(g.branchId());
        Integer day = r.meetingDay() != null ? r.meetingDay() : (Integer) (pr.get("meeting_day") == null ? null : ((Number) pr.get("meeting_day")).intValue());
        Time time = r.meetingTime() != null ? Time.valueOf(r.meetingTime()) : (Time) pr.get("meeting_time");
        try {
            jdbc.update("insert into small_group (id, organization_id, branch_id, name, category, audience, meeting_day, meeting_time, location, zone, open_to_join, start_date, parent_group_id,"
                            + " status, created_at, created_by) values (:id, :o, :b, :n, :c, :a, :md, :mt, :l, :z, false, :sd, :pg, 'DRAFT', :at, :by)",
                    new MapSqlParameterSource("id", childId).addValue("o", g.orgId()).addValue("b", g.branchId()).addValue("n", name).addValue("c", pr.get("category"))
                            .addValue("a", pr.get("audience")).addValue("md", day).addValue("mt", time)
                            .addValue("l", GroupSupport.hasText(r.location()) ? GroupSupport.trim(r.location(), 160, "ubicación") : pr.get("location"))
                            .addValue("z", GroupSupport.hasText(r.zone()) ? GroupSupport.trim(r.zone(), 80, "zona") : pr.get("zone")).addValue("sd", java.sql.Date.valueOf(today))
                            .addValue("pg", id).addValue("at", Timestamp.from(clock.instant())).addValue("by", actor.ownerId()));
        } catch (DataIntegrityViolationException e) {
            throw new Exceptions("error.group.nameTaken", HttpStatus.CONFLICT);
        }
        GroupSupport.GroupRow child = support.loadRaw(childId);
        for (UUID pid : ids) {
            jdbc.update("update group_member set status = 'LEFT', left_at = greatest(:t, joined_at), left_reason = 'MULTIPLIED' where group_id = :g and person_id = :p and status = 'ACTIVE'",
                    new MapSqlParameterSource("t", java.sql.Date.valueOf(today)).addValue("g", id).addValue("p", pid));
            addChecked(child, support.person(g.orgId(), pid), pid.equals(leader) ? "LEADER" : pid.equals(co) ? "COLEADER" : "MEMBER", actor.ownerId(), actor.ownerId());
        }
        jdbc.update("update small_group set status = 'ACTIVE', updated_at = :at, version = version + 1 where id = :c", new MapSqlParameterSource("at", Timestamp.from(clock.instant())).addValue("c", childId));
        support.assertLeadership(support.loadRaw(childId), true);                                                                     // [V3][V4] del hijo
        support.assertLeadership(support.loadRaw(id), true);                                                                          // y del padre tras el movimiento
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("child", childId.toString());
        d.put("moved", ids.size());
        audit.record(new AuditService.Command(MODULE, "MULTIPLY", ENTITY, id, g.orgId(), g.branchId(), d));
        audit.record(new AuditService.Command(MODULE, "CREATE", ENTITY, childId, g.orgId(), g.branchId(), Map.of("name", name, "parent", id.toString())));
        return new GroupDtos.MultiplyResult(getRaw(childId), getRaw(id), ids.size());
    }

    // ---------------------------------------------------------------- exportar

    public record ExportResult(byte[] content, int rows) {
    }

    @Transactional
    public ExportResult export(AuthenticatedActor actor, AccessScope scope, GroupDtos.Search req) {
        authz.require(actor, MODULE, Action.X);
        GroupDtos.Search.Filters f = req == null || req.filters() == null ? new GroupDtos.Search.Filters(null, null, null, null, null, null) : req.filters();
        MapSqlParameterSource ps = new MapSqlParameterSource();
        StringBuilder w = new StringBuilder(GroupSupport.visible(scope, ps, "g"));
        filters(f, w, ps);
        List<GroupDtos.GroupResponse> rows = jdbc.query(SELECT + " where " + w + " order by lower(b.name), lower(g.name) limit 5000", ps, (rs, i) -> map(rs));
        List<List<Object>> data = new ArrayList<>();
        for (GroupDtos.GroupResponse g : rows) {
            data.add(java.util.Arrays.asList(g.name(), g.branchName(), g.category(), g.audience(), g.status(), g.leaderName(), g.members(), g.capacity(), g.meetingDay(),
                    g.meetingTime() == null ? null : g.meetingTime().toString(), g.zone()));
        }
        byte[] bytes = XlsxWriter.write("Grupos", List.of("Grupo", "Sede", "Categoría", "Público", "Estado", "Líder", "Integrantes", "Cupo", "Día", "Hora", "Zona"), data);
        audit.record(new AuditService.Command(MODULE, "EXPORT", ENTITY, scope.organizationId(), scope.organizationId(), null, Map.of("rows", rows.size())));
        return new ExportResult(bytes, rows.size());
    }
}
