package pe.dcs.app.features.volunteer.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.notification.domain.NotificationType;
import pe.dcs.app.features.notification.service.NotificationService;
import pe.dcs.app.features.volunteer.dto.VolunteerDtos;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.AuthorizationService;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PaginationResponse;

import java.sql.Time;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * M11b · Planes de servicio (culto o evento, en una fecha) y sus turnos. DRAFT (se arma libremente) → PUBLISHED [V13] (notifica a los propuestos) → CLOSED (tras el
 * servicio). [V4] el ministerio del turno debe estar activo en la sede del plan · [V7][V8] horario y cantidad del turno.
 */
@Service
@RequiredArgsConstructor
public class ServicePlanService {

    static final String MODULE = VolunteerSupport.MODULE;
    private static final String ENTITY = "ServicePlan";
    private static final Set<String> CONTEXT_TYPES = Set.of("SERVICE", "EVENT");

    private final NamedParameterJdbcTemplate jdbc;
    private final VolunteerSupport support;
    private final AuthorizationService authz;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Clock clock;

    private static final String PLAN_SELECT = "select s.id, s.branch_id, br.name as branch_name, s.plan_date, s.context_type, s.context_id, s.title, s.status, s.published_at, s.version,"
            + " (select count(*) from shift_slot sl where sl.plan_id = s.id) as slots,"
            + " (select coalesce(sum(greatest(sl.needed - (select count(*) from shift_assignment a where a.slot_id = sl.id and a.status in ('PROPOSED','CONFIRMED','SERVED')), 0)), 0)"
            + "  from shift_slot sl where sl.plan_id = s.id) as vacancies"
            + " from service_plan s join branch br on br.id = s.branch_id";

    private VolunteerDtos.PlanResponse mapPlan(java.sql.ResultSet rs, boolean detail) throws java.sql.SQLException {
        UUID id = (UUID) rs.getObject("id");
        return new VolunteerDtos.PlanResponse(id, (UUID) rs.getObject("branch_id"), rs.getString("branch_name"), rs.getDate("plan_date").toLocalDate(), rs.getString("context_type"),
                (UUID) rs.getObject("context_id"), rs.getString("title"), rs.getString("status"), rs.getTimestamp("published_at") == null ? null : rs.getTimestamp("published_at").toInstant(),
                rs.getInt("slots"), rs.getInt("vacancies"), rs.getLong("version"), detail ? slotsOf(id) : null);
    }

    private static final String SLOT_SELECT = "select sl.id, sl.branch_ministry_id, m.name as mname, sl.position_id, po.name as poname, sl.needed, sl.start_time, sl.end_time, sl.notes,"
            + " sl.version, m.requires_screening, m.screening_type,"
            + " (select count(*) from shift_assignment a where a.slot_id = sl.id and a.status = 'CONFIRMED') as confirmed,"
            + " (select count(*) from shift_assignment a where a.slot_id = sl.id and a.status = 'PROPOSED') as proposed"
            + " from shift_slot sl join branch_ministry bm on bm.id = sl.branch_ministry_id join ministry m on m.id = bm.ministry_id left join ministry_position po on po.id = sl.position_id";

    private static final String ASSIGN_SELECT = "select a.id, a.slot_id, a.person_id, trim(p.first_name || ' ' || p.last_name) as pname, a.status, a.decline_reason, a.responded_at,"
            + " p.birth_date from shift_assignment a join person p on p.id = a.person_id";

    private VolunteerDtos.AssignmentResponse mapAssignment(java.sql.ResultSet rs, LocalDate today, boolean requiresScreening, String screeningType) throws java.sql.SQLException {
        UUID person = (UUID) rs.getObject("person_id");
        java.sql.Date birth = rs.getDate("birth_date");
        boolean minor = birth != null && java.time.Period.between(birth.toLocalDate(), today).getYears() < pe.dcs.app.features.ministry.service.MinistrySupport.ADULT_AGE;
        String screening = support.ministry().screeningState(person, requiresScreening, screeningType, today);
        return new VolunteerDtos.AssignmentResponse((UUID) rs.getObject("id"), (UUID) rs.getObject("slot_id"), person, rs.getString("pname"), rs.getString("status"),
                rs.getString("decline_reason"), rs.getTimestamp("responded_at") == null ? null : rs.getTimestamp("responded_at").toInstant(), screening, minor);
    }

    List<VolunteerDtos.AssignmentResponse> assignmentsOf(UUID slotId, LocalDate today, boolean requiresScreening, String screeningType) {
        return jdbc.query(ASSIGN_SELECT + " where a.slot_id = :s order by (a.status = 'CONFIRMED') desc, (a.status = 'PROPOSED') desc, lower(p.first_name)",
                new MapSqlParameterSource("s", slotId), (rs, i) -> mapAssignment(rs, today, requiresScreening, screeningType));
    }

    private List<VolunteerDtos.SlotResponse> slotsOf(UUID planId) {
        LocalDate today = LocalDate.now(clock);
        return jdbc.query(SLOT_SELECT + " where sl.plan_id = :p order by sl.start_time, lower(m.name)", new MapSqlParameterSource("p", planId), (rs, i) -> {
            UUID id = (UUID) rs.getObject("id");
            boolean rs2 = rs.getBoolean("requires_screening");
            String st2 = rs.getString("screening_type");
            return new VolunteerDtos.SlotResponse(id, (UUID) rs.getObject("branch_ministry_id"), rs.getString("mname"), (UUID) rs.getObject("position_id"), rs.getString("poname"),
                    rs.getInt("needed"), rs.getTime("start_time").toLocalTime(), rs.getTime("end_time").toLocalTime(), rs.getString("notes"), rs.getInt("confirmed"), rs.getInt("proposed"),
                    rs.getLong("version"), assignmentsOf(id, today, rs2, st2));
        });
    }

    // ---------------------------------------------------------------- consulta

    @Transactional(readOnly = true)
    public PageResponse<VolunteerDtos.PlanResponse> search(AccessScope scope, VolunteerDtos.PlanSearch req) {
        VolunteerDtos.PlanSearch.Filters f = req == null || req.filters() == null ? new VolunteerDtos.PlanSearch.Filters(null, null, null, null) : req.filters();
        MapSqlParameterSource ps = new MapSqlParameterSource();
        StringBuilder w = new StringBuilder(VolunteerSupport.visiblePlan(scope, ps, "s"));
        if (f.branchId() != null) {
            w.append(" and s.branch_id = :fb");
            ps.addValue("fb", f.branchId());
        }
        if (f.status() != null && !f.status().isBlank()) {
            String st = f.status().trim().toUpperCase();
            if (!Set.of("DRAFT", "PUBLISHED", "CLOSED").contains(st)) {
                throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "estado");
            }
            w.append(" and s.status = :st");
            ps.addValue("st", st);
        }
        if (f.from() != null) {
            w.append(" and s.plan_date >= :df");
            ps.addValue("df", java.sql.Date.valueOf(f.from()));
        }
        if (f.to() != null) {
            w.append(" and s.plan_date <= :dt");
            ps.addValue("dt", java.sql.Date.valueOf(f.to()));
        }
        Long total = jdbc.queryForObject("select count(*) from service_plan s where " + w, ps, Long.class);
        int size = req == null || req.pagination() == null ? 50 : Math.max(1, Math.min(req.pagination().getSize(), 200));
        int page = req == null || req.pagination() == null ? 0 : Math.max(0, req.pagination().getPage());
        ps.addValue("lim", size).addValue("off", (long) page * size);
        List<VolunteerDtos.PlanResponse> rows = jdbc.query(PLAN_SELECT + " where " + w + " order by s.plan_date desc, lower(br.name) limit :lim offset :off", ps, (rs, i) -> mapPlan(rs, false));
        long t = total == null ? 0 : total;
        return new PageResponse<>(rows, new PaginationResponse((int) t, (int) Math.ceil(t / (double) size), size, page));
    }

    @Transactional(readOnly = true)
    public VolunteerDtos.PlanResponse get(AccessScope scope, UUID id) {
        support.loadPlan(scope, id);
        return getRaw(id);
    }

    VolunteerDtos.PlanResponse getRaw(UUID id) {
        return jdbc.query(PLAN_SELECT + " where s.id = :id", new MapSqlParameterSource("id", id), (rs, i) -> mapPlan(rs, true)).stream().findFirst()
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    private static void notOwn(AccessScope scope) {
        if (VolunteerSupport.isOwn(scope)) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
    }

    // ---------------------------------------------------------------- alta y edición del plan

    @Transactional
    public VolunteerDtos.PlanResponse create(AuthenticatedActor actor, AccessScope scope, VolunteerDtos.PlanRequest r) {
        authz.require(actor, MODULE, Action.C);
        notOwn(scope);
        if (r == null || r.branchId() == null || r.planDate() == null || r.contextType() == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "sede, fecha y tipo");
        }
        if (!scope.canSeeBranch(r.branchId())) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        String ctxType = r.contextType().trim().toUpperCase();
        if (!CONTEXT_TYPES.contains(ctxType)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "tipo");
        }
        String title = r.title() == null ? null : r.title().trim();
        if (ctxType.equals("SERVICE") && r.contextId() != null) {
            List<Object[]> svc = jdbc.query("select name, branch_id, status from church_service where id = :id",
                    new MapSqlParameterSource("id", r.contextId()), (rs, i) -> new Object[]{rs.getString(1), rs.getObject(2), rs.getString(3)});
            if (svc.isEmpty() || !r.branchId().equals(svc.get(0)[1])) {
                throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
            }
            if (title == null || title.isEmpty()) {
                title = (String) svc.get(0)[0];
            }
        }
        if (title == null || title.isEmpty()) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "título");
        }
        if (title.length() > 120) {
            throw new Exceptions("error.common.tooLong", HttpStatus.BAD_REQUEST, "título", 120);
        }
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("insert into service_plan (id, organization_id, branch_id, plan_date, context_type, context_id, title, status, created_at, created_by)"
                            + " values (:id, :o, :b, :d, :ct, :c, :t, 'DRAFT', :at, :by)",
                    new MapSqlParameterSource("id", id).addValue("o", scope.organizationId()).addValue("b", r.branchId()).addValue("d", java.sql.Date.valueOf(r.planDate()))
                            .addValue("ct", ctxType).addValue("c", r.contextId()).addValue("t", title).addValue("at", Timestamp.from(clock.instant())).addValue("by", actor.ownerId()));
        } catch (DataIntegrityViolationException e) {
            throw new Exceptions("error.shift.planExists", HttpStatus.CONFLICT);
        }
        audit.record(new AuditService.Command(MODULE, "PLAN_CREATE", ENTITY, id, scope.organizationId(), r.branchId(), Map.of("title", title, "date", r.planDate().toString())));
        return getRaw(id);
    }

    @Transactional
    public VolunteerDtos.PlanResponse update(AuthenticatedActor actor, AccessScope scope, UUID id, VolunteerDtos.PlanUpdate r) {
        authz.require(actor, MODULE, Action.E);
        notOwn(scope);
        VolunteerSupport.PlanRow p = support.lockPlan(scope, id);
        if (!"DRAFT".equals(p.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, p.status());
        }
        String title = r == null || r.title() == null || r.title().isBlank() ? p.title() : r.title().trim();
        if (title.length() > 120) {
            throw new Exceptions("error.common.tooLong", HttpStatus.BAD_REQUEST, "título", 120);
        }
        LocalDate date = r == null || r.planDate() == null ? p.planDate() : r.planDate();
        int n = jdbc.update("update service_plan set plan_date = :d, title = :t, updated_at = :at, updated_by = :by, version = version + 1"
                        + " where id = :id and (cast(:v as bigint) is null or version = :v)",
                new MapSqlParameterSource("d", java.sql.Date.valueOf(date)).addValue("t", title).addValue("at", Timestamp.from(clock.instant())).addValue("by", actor.ownerId())
                        .addValue("id", id).addValue("v", r == null ? null : r.version()));
        if (n == 0) {
            throw new Exceptions("error.common.concurrentUpdate", HttpStatus.CONFLICT);
        }
        audit.record(new AuditService.Command(MODULE, "PLAN_UPDATE", ENTITY, id, p.orgId(), p.branchId(), Map.of("title", title)));
        return getRaw(id);
    }

    @Transactional
    public void delete(AuthenticatedActor actor, AccessScope scope, UUID id) {
        authz.require(actor, MODULE, Action.D);
        notOwn(scope);
        VolunteerSupport.PlanRow p = support.lockPlan(scope, id);
        if (!"DRAFT".equals(p.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, p.status());
        }
        MapSqlParameterSource ps = new MapSqlParameterSource("p", id);
        jdbc.update("delete from shift_assignment where slot_id in (select id from shift_slot where plan_id = :p)", ps);
        jdbc.update("delete from shift_slot where plan_id = :p", ps);
        jdbc.update("delete from service_plan where id = :p", ps);
        audit.record(new AuditService.Command(MODULE, "PLAN_DELETE", ENTITY, id, p.orgId(), p.branchId(), Map.of("title", p.title())));
    }

    // ---------------------------------------------------------------- turnos

    @Transactional
    public VolunteerDtos.PlanResponse addSlot(AuthenticatedActor actor, AccessScope scope, UUID planId, VolunteerDtos.SlotRequest r) {
        authz.require(actor, MODULE, Action.C);
        VolunteerSupport.PlanRow p = support.lockPlan(scope, planId);
        if (!"DRAFT".equals(p.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, p.status());
        }
        if (r == null || r.branchMinistryId() == null || r.startTime() == null || r.endTime() == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "ministerio y horario");
        }
        pe.dcs.app.features.ministry.service.MinistrySupport.BranchMinistryRow bm = support.ministry().loadBmRaw(r.branchMinistryId());
        if (!bm.orgId().equals(p.orgId()) || !bm.branchId().equals(p.branchId())) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        if (!bm.open()) {
            throw new Exceptions("error.ministry.notActive", HttpStatus.UNPROCESSABLE_ENTITY);                                       // [V4]
        }
        if (!support.leads(scope, r.branchMinistryId())) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
        if (r.positionId() != null) {
            List<UUID> pos = jdbc.queryForList("select id from ministry_position where id = :id and ministry_id = :m",
                    new MapSqlParameterSource("id", r.positionId()).addValue("m", bm.ministryId()), UUID.class);
            if (pos.isEmpty()) {
                throw new Exceptions("error.ministry.positionInvalid", HttpStatus.UNPROCESSABLE_ENTITY);
            }
        }
        int needed = r.needed() == null ? 1 : r.needed();
        if (needed < 1 || needed > 100) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "cantidad");                                        // [V8]
        }
        if (!r.endTime().isAfter(r.startTime())) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "horario");                                         // [V7][V8]
        }
        UUID id = UUID.randomUUID();
        jdbc.update("insert into shift_slot (id, plan_id, branch_ministry_id, position_id, needed, start_time, end_time, notes, created_at, created_by)"
                        + " values (:id, :p, :bm, :po, :n, :st, :et, :nt, :at, :by)",
                new MapSqlParameterSource("id", id).addValue("p", planId).addValue("bm", r.branchMinistryId()).addValue("po", r.positionId()).addValue("n", needed)
                        .addValue("st", Time.valueOf(r.startTime())).addValue("et", Time.valueOf(r.endTime())).addValue("nt", trim(r.notes(), 300))
                        .addValue("at", Timestamp.from(clock.instant())).addValue("by", actor.ownerId()));
        audit.record(new AuditService.Command(MODULE, "SLOT_ADD", "ShiftSlot", id, p.orgId(), p.branchId(), Map.of("ministry", bm.ministryName(), "needed", needed)));
        return getRaw(planId);
    }

    @Transactional
    public VolunteerDtos.PlanResponse updateSlot(AuthenticatedActor actor, AccessScope scope, UUID planId, UUID slotId, VolunteerDtos.SlotRequest r) {
        authz.require(actor, MODULE, Action.E);
        VolunteerSupport.PlanRow p = support.lockPlan(scope, planId);
        if (!"DRAFT".equals(p.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, p.status());
        }
        VolunteerSupport.SlotRow s = support.loadSlot(planId, slotId);
        if (!support.leads(scope, s.branchMinistryId())) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
        int needed = r == null || r.needed() == null ? s.needed() : r.needed();
        LocalTime start = r == null || r.startTime() == null ? s.start() : r.startTime();
        LocalTime end = r == null || r.endTime() == null ? s.end() : r.endTime();
        if (needed < 1 || needed > 100 || !end.isAfter(start)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "turno");
        }
        jdbc.update("update shift_slot set needed = :n, start_time = :st, end_time = :et, notes = :nt, version = version + 1 where id = :id",
                new MapSqlParameterSource("n", needed).addValue("st", Time.valueOf(start)).addValue("et", Time.valueOf(end))
                        .addValue("nt", r == null ? null : trim(r.notes(), 300)).addValue("id", slotId));
        audit.record(new AuditService.Command(MODULE, "SLOT_UPDATE", "ShiftSlot", slotId, p.orgId(), p.branchId(), Map.of("needed", needed)));
        return getRaw(planId);
    }

    @Transactional
    public VolunteerDtos.PlanResponse deleteSlot(AuthenticatedActor actor, AccessScope scope, UUID planId, UUID slotId) {
        authz.require(actor, MODULE, Action.D);
        VolunteerSupport.PlanRow p = support.lockPlan(scope, planId);
        if (!"DRAFT".equals(p.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, p.status());
        }
        VolunteerSupport.SlotRow s = support.loadSlot(planId, slotId);
        if (!support.leads(scope, s.branchMinistryId())) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
        jdbc.update("delete from shift_assignment where slot_id = :id", new MapSqlParameterSource("id", slotId));
        jdbc.update("delete from shift_slot where id = :id", new MapSqlParameterSource("id", slotId));
        audit.record(new AuditService.Command(MODULE, "SLOT_DELETE", "ShiftSlot", slotId, p.orgId(), p.branchId(), Map.of("id", slotId.toString())));
        return getRaw(planId);
    }

    // ---------------------------------------------------------------- publicar y cerrar

    /** [V13] Exige cobertura completa (todos los turnos con cupo cubierto) o confirmación explícita de vacantes; notifica a cada persona propuesta. */
    @Transactional
    public VolunteerDtos.PlanResponse publish(AuthenticatedActor actor, AccessScope scope, UUID id, VolunteerDtos.PublishRequest r) {
        authz.require(actor, MODULE, Action.P);
        notOwn(scope);
        VolunteerSupport.PlanRow p = support.lockPlan(scope, id);
        if (!"DRAFT".equals(p.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, p.status());
        }
        Integer slotCount = jdbc.queryForObject("select count(*) from shift_slot where plan_id = :p", new MapSqlParameterSource("p", id), Integer.class);
        if (slotCount == null || slotCount == 0) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "turnos");
        }
        Integer vacancies = jdbc.queryForObject("select coalesce(sum(greatest(sl.needed - (select count(*) from shift_assignment a where a.slot_id = sl.id"
                + " and a.status in ('PROPOSED','CONFIRMED','SERVED')), 0)), 0) from shift_slot sl where sl.plan_id = :p", new MapSqlParameterSource("p", id), Integer.class);
        boolean force = r != null && Boolean.TRUE.equals(r.force());
        if (vacancies != null && vacancies > 0 && !force) {
            throw new Exceptions("error.shift.vacancies", HttpStatus.UNPROCESSABLE_ENTITY, vacancies);                              // [V13]
        }
        jdbc.update("update service_plan set status = 'PUBLISHED', published_at = :at, updated_at = :at, updated_by = :by, version = version + 1 where id = :id",
                new MapSqlParameterSource("at", Timestamp.from(clock.instant())).addValue("by", actor.ownerId()).addValue("id", id));
        List<Object[]> proposed = jdbc.query("select a.person_id, sl.start_time, m.name from shift_assignment a join shift_slot sl on sl.id = a.slot_id"
                        + " join branch_ministry bm on bm.id = sl.branch_ministry_id join ministry m on m.id = bm.ministry_id where sl.plan_id = :p and a.status = 'PROPOSED'",
                new MapSqlParameterSource("p", id), (rs, i) -> new Object[]{rs.getObject(1), rs.getTime(2).toLocalTime(), rs.getString(3)});
        for (Object[] row : proposed) {
            UUID personId = (UUID) row[0];
            notifications.toPersons(NotificationType.SHIFT_ASSIGNED, p.orgId(), List.of(personId),
                    Map.of("ministry", (String) row[2], "date", p.planDate().toString(), "time", row[1].toString()), "/app/volunteer-scheduling", "SHIFT_PUBLISHED:" + id + ":" + personId);
        }
        audit.record(new AuditService.Command(MODULE, "PLAN_PUBLISH", ENTITY, id, p.orgId(), p.branchId(), Map.of("vacancies", vacancies == null ? 0 : vacancies, "notified", proposed.size())));
        return getRaw(id);
    }

    /** Cierra el plan (tras el servicio); no bloquea si aún quedan asignaciones sin marcar SERVED/NO_SHOW, eso se hace turno por turno. */
    @Transactional
    public VolunteerDtos.PlanResponse close(AuthenticatedActor actor, AccessScope scope, UUID id) {
        authz.require(actor, MODULE, Action.S);
        notOwn(scope);
        VolunteerSupport.PlanRow p = support.lockPlan(scope, id);
        if (!"PUBLISHED".equals(p.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, p.status());
        }
        jdbc.update("update service_plan set status = 'CLOSED', closed_at = :at, updated_at = :at, updated_by = :by, version = version + 1 where id = :id",
                new MapSqlParameterSource("at", Timestamp.from(clock.instant())).addValue("by", actor.ownerId()).addValue("id", id));
        audit.record(new AuditService.Command(MODULE, "PLAN_CLOSE", ENTITY, id, p.orgId(), p.branchId(), Map.of("id", id.toString())));
        return getRaw(id);
    }

    private static String trim(String s, int max) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    // ---------------------------------------------------------------- exportar

    public record ExportResult(byte[] content, int rows) {
    }

    @Transactional
    public ExportResult export(AuthenticatedActor actor, AccessScope scope, VolunteerDtos.PlanSearch req) {
        authz.require(actor, MODULE, Action.X);
        MapSqlParameterSource ps = new MapSqlParameterSource();
        VolunteerDtos.PlanSearch.Filters f = req == null || req.filters() == null ? new VolunteerDtos.PlanSearch.Filters(null, null, null, null) : req.filters();
        StringBuilder w = new StringBuilder(VolunteerSupport.visiblePlan(scope, ps, "s"));
        if (f.branchId() != null) {
            w.append(" and s.branch_id = :fb");
            ps.addValue("fb", f.branchId());
        }
        List<VolunteerDtos.PlanResponse> rows = jdbc.query(PLAN_SELECT + " where " + w + " order by s.plan_date desc limit 2000", ps, (rs, i) -> mapPlan(rs, false));
        List<List<Object>> data = new ArrayList<>();
        for (VolunteerDtos.PlanResponse r : rows) {
            data.add(java.util.Arrays.asList(r.title(), r.branchName(), r.planDate().toString(), r.status(), r.slots(), r.vacancies()));
        }
        byte[] bytes = pe.dcs.app.shared.export.XlsxWriter.write("Turnos", List.of("Plan", "Sede", "Fecha", "Estado", "Turnos", "Vacantes"), data);
        audit.record(new AuditService.Command(MODULE, "EXPORT", ENTITY, scope.organizationId(), scope.organizationId(), null, Map.of("rows", rows.size())));
        return new ExportResult(bytes, rows.size());
    }
}
