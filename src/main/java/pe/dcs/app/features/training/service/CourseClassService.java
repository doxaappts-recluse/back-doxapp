package pe.dcs.app.features.training.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.attendance.dto.AttendanceDtos;
import pe.dcs.app.features.attendance.service.AttendanceService;
import pe.dcs.app.features.facility.service.FacilitySupport;
import pe.dcs.app.features.facility.service.ReservationService;
import pe.dcs.app.features.facility.service.SpaceService;
import pe.dcs.app.features.notification.domain.NotificationType;
import pe.dcs.app.features.notification.service.NotificationService;
import pe.dcs.app.features.training.dto.TrainingDtos;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.AuthorizationService;
import pe.dcs.app.security.authz.ContractGate;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PaginationResponse;

import java.sql.Time;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * M13 · Dictado (course_class): un curso ofrecido en una sede con docente y horario. PLANNED → IN_PROGRESS (abre asistencia con el
 * núcleo M09, contexto CLASS: una sesión de asistencia por fecha de clase) → COMPLETED (todas las matrículas resueltas [V12]) |
 * CANCELLED (motivo, avisa a los matriculados).
 */
@Service
@RequiredArgsConstructor
public class CourseClassService {

    private static final String MODULE = TrainingSupport.MODULE;
    private static final String ENTITY = "CourseClass";
    private static final String CONTEXT = "CLASS";
    private static final String RESERVATION_SOURCE = "BIBLE_CLASS";
    private static final Set<String> STATUSES = Set.of("PLANNED", "IN_PROGRESS", "COMPLETED", "CANCELLED");
    private static final Set<String> ATT = Set.of("PRESENT", "LATE", "EXCUSED", "ABSENT");
    private static final int MAX_SESSIONS = 80;

    private final NamedParameterJdbcTemplate jdbc;
    private final TrainingSupport support;
    private final CurriculumService curricula;
    private final AttendanceService attendance;
    private final NotificationService notifications;
    private final AuthorizationService authz;
    private final AuditService audit;
    private final Clock clock;
    private final ContractGate contractGate;
    private final ObjectProvider<SpaceService> spaceProvider;
    private final ObjectProvider<ReservationService> reservationProvider;

    record ClassRow(UUID id, UUID orgId, UUID branchId, UUID courseId, UUID teacherId, Integer dayOfWeek, LocalTime startTime, LocalTime endTime,
                    String location, LocalDate startDate, LocalDate endDate, int capacity, String status, UUID spaceId, long version) {
    }

    // ---------------------------------------------------------------- consulta

    @Transactional(readOnly = true)
    public PageResponse<TrainingDtos.ClassSummary> search(AccessScope scope, TrainingDtos.ClassSearch req) {
        TrainingDtos.ClassSearch.ClassFilters f = req == null || req.filters() == null
                ? new TrainingDtos.ClassSearch.ClassFilters(null, null, null, null, null, null, null) : req.filters();
        MapSqlParameterSource ps = new MapSqlParameterSource();
        StringBuilder w = new StringBuilder(TrainingSupport.visibleClass(scope, ps, "t"));
        if (f.branchId() != null) {
            w.append(" and t.branch_id = :fb");
            ps.addValue("fb", f.branchId());
        }
        if (f.courseId() != null) {
            w.append(" and t.course_id = :fc");
            ps.addValue("fc", f.courseId());
        }
        if (f.teacherId() != null) {
            w.append(" and t.teacher_person_id = :ft");
            ps.addValue("ft", f.teacherId());
        }
        if (TrainingSupport.hasText(f.status())) {
            String s = f.status().trim().toUpperCase();
            if (!STATUSES.contains(s)) {
                throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "estado");
            }
            w.append(" and t.status = :fs");
            ps.addValue("fs", s);
        }
        if (f.from() != null) {
            w.append(" and t.end_date >= :df");
            ps.addValue("df", java.sql.Date.valueOf(f.from()));
        }
        if (f.to() != null) {
            w.append(" and t.start_date <= :dt");
            ps.addValue("dt", java.sql.Date.valueOf(f.to()));
        }
        String from = " from course_class t join course k on k.id = t.course_id left join curriculum cu on cu.id = k.curriculum_id"
                + " join branch b on b.id = t.branch_id join person p on p.id = t.teacher_person_id left join space sp on sp.id = t.space_id where ";
        Long total = jdbc.queryForObject("select count(*)" + from + w, ps, Long.class);
        int size = req == null || req.pagination() == null ? 20 : Math.max(1, Math.min(req.pagination().getSize(), 200));
        int page = req == null || req.pagination() == null ? 0 : Math.max(0, req.pagination().getPage());
        ps.addValue("lim", size).addValue("off", (long) page * size);
        List<TrainingDtos.ClassSummary> rows = jdbc.query(SUMMARY + from + w + " order by t.start_date desc, t.id limit :lim offset :off", ps, (rs, i) -> summary(rs));
        long t = total == null ? 0 : total;
        return new PageResponse<>(rows, new PaginationResponse((int) t, (int) Math.ceil(t / (double) size), size, page));
    }

    @Transactional(readOnly = true)
    public TrainingDtos.ClassResponse get(AccessScope scope, UUID id) {
        return build(load(scope, id));
    }

    private static final String SUMMARY = "select t.id, t.course_id, k.name as course_name, k.order_num, cu.name as curriculum_name, t.branch_id, b.name as branch_name,"
            + " t.teacher_person_id, trim(p.first_name || ' ' || p.last_name) as teacher_name, t.day_of_week, t.start_time, t.end_time, t.location,"
            + " t.start_date, t.end_date, t.capacity, t.status, t.cancel_reason, t.created_at, t.version, t.space_id, sp.name as space_name,"
            + " (select count(*) from enrollment e where e.class_id = t.id and e.status <> 'WITHDRAWN') as enrolled";

    private TrainingDtos.ClassSummary summary(java.sql.ResultSet rs) throws java.sql.SQLException {
        Time st = rs.getTime("start_time");
        Time et = rs.getTime("end_time");
        return new TrainingDtos.ClassSummary((UUID) rs.getObject("id"), (UUID) rs.getObject("course_id"), rs.getString("course_name"), (Integer) rs.getObject("order_num"),
                rs.getString("curriculum_name"), (UUID) rs.getObject("branch_id"), rs.getString("branch_name"), (UUID) rs.getObject("teacher_person_id"),
                rs.getString("teacher_name"), (Integer) rs.getObject("day_of_week"), st == null ? null : st.toLocalTime(), et == null ? null : et.toLocalTime(),
                rs.getString("location"), rs.getDate("start_date").toLocalDate(), rs.getDate("end_date").toLocalDate(), rs.getInt("capacity"), rs.getString("status"),
                rs.getInt("enrolled"), rs.getTimestamp("created_at").toInstant(), rs.getLong("version"), (UUID) rs.getObject("space_id"), rs.getString("space_name"));
    }

    ClassRow load(AccessScope scope, UUID id) {
        MapSqlParameterSource ps = new MapSqlParameterSource("id", id);
        String vis = TrainingSupport.visibleClass(scope, ps, "t");
        return jdbc.query("select t.* from course_class t where t.id = :id and " + vis, ps, (rs, i) -> row(rs)).stream().findFirst()
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    private ClassRow lock(AccessScope scope, UUID id) {
        load(scope, id);
        return jdbc.query("select * from course_class where id = :id for update", new MapSqlParameterSource("id", id), (rs, i) -> row(rs)).get(0);
    }

    private static ClassRow row(java.sql.ResultSet rs) throws java.sql.SQLException {
        Time st = rs.getTime("start_time");
        Time et = rs.getTime("end_time");
        return new ClassRow((UUID) rs.getObject("id"), (UUID) rs.getObject("organization_id"), (UUID) rs.getObject("branch_id"), (UUID) rs.getObject("course_id"),
                (UUID) rs.getObject("teacher_person_id"), (Integer) rs.getObject("day_of_week"), st == null ? null : st.toLocalTime(), et == null ? null : et.toLocalTime(),
                rs.getString("location"), rs.getDate("start_date").toLocalDate(), rs.getDate("end_date").toLocalDate(), rs.getInt("capacity"), rs.getString("status"),
                (UUID) rs.getObject("space_id"), rs.getLong("version"));
    }

    private TrainingDtos.ClassResponse build(ClassRow c) {
        TrainingDtos.ClassSummary s = jdbc.query(SUMMARY + " from course_class t join course k on k.id = t.course_id left join curriculum cu on cu.id = k.curriculum_id"
                + " join branch b on b.id = t.branch_id join person p on p.id = t.teacher_person_id left join space sp on sp.id = t.space_id where t.id = :id",
                new MapSqlParameterSource("id", c.id()), (rs, i) -> summary(rs)).get(0);
        List<TrainingDtos.EnrollmentBrief> enrollments = jdbc.query("select e.id, e.person_id, trim(p.first_name || ' ' || p.last_name) as name, e.status, e.final_grade"
                + " from enrollment e join person p on p.id = e.person_id where e.class_id = :id order by name",
                new MapSqlParameterSource("id", c.id()), (rs, i) -> {
                    java.math.BigDecimal g = rs.getBigDecimal("final_grade");
                    return new TrainingDtos.EnrollmentBrief((UUID) rs.getObject("id"), (UUID) rs.getObject("person_id"), rs.getString("name"), rs.getString("status"),
                            g == null ? null : g.toPlainString());
                });
        String cancelReason = jdbc.queryForObject("select cancel_reason from course_class where id = :id", new MapSqlParameterSource("id", c.id()), String.class);
        return new TrainingDtos.ClassResponse(s, cancelReason, enrollments);
    }

    // ---------------------------------------------------------------- alta y edición

    @Transactional
    public TrainingDtos.ClassResponse create(AuthenticatedActor actor, AccessScope scope, TrainingDtos.ClassRequest r) {
        authz.require(actor, MODULE, Action.C);
        if (r == null || r.courseId() == null || r.branchId() == null || r.teacherId() == null || r.startDate() == null || r.endDate() == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "curso, sede, docente y fechas");
        }
        support.assertActiveBranch(scope, r.branchId());
        CurriculumService.CourseRow course = curricula.course(scope, r.courseId());
        if (!"ACTIVE".equals(course.status()) || (course.curriculumId() != null && !"ACTIVE".equals(course.curriculumStatus()))) {
            throw new Exceptions("error.training.curriculumRetired", HttpStatus.CONFLICT);                               // [V6]
        }
        support.assertActiveAdult(scope.organizationId(), r.teacherId());                                                // [V6]
        if (r.endDate().isBefore(r.startDate())) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "fechas");                              // [V6]
        }
        int capacity = r.capacity() == null ? 20 : r.capacity();
        if (capacity < 1) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "cupo");                                // [V6]
        }
        validateSpace(scope.organizationId(), r.branchId(), r.spaceId());                                                // [M13→M16]
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("insert into course_class (id, organization_id, branch_id, course_id, teacher_person_id, day_of_week, start_time, end_time, location,"
                        + " start_date, end_date, capacity, space_id, status, created_at, created_by, updated_at, updated_by)"
                        + " values (:id, :o, :b, :c, :t, :dow, :st, :et, :loc, :sd, :ed, :cap, :sp, 'PLANNED', :at, :by, :at, :by)",
                new MapSqlParameterSource("id", id).addValue("o", scope.organizationId()).addValue("b", r.branchId()).addValue("c", r.courseId())
                        .addValue("t", r.teacherId()).addValue("dow", r.dayOfWeek()).addValue("st", r.startTime() == null ? null : Time.valueOf(r.startTime()))
                        .addValue("et", r.endTime() == null ? null : Time.valueOf(r.endTime())).addValue("loc", TrainingSupport.trim(r.location(), 160, "lugar"))
                        .addValue("sd", java.sql.Date.valueOf(r.startDate())).addValue("ed", java.sql.Date.valueOf(r.endDate())).addValue("cap", capacity)
                        .addValue("sp", r.spaceId()).addValue("at", now).addValue("by", actor.ownerId()));
        audit.record(new AuditService.Command(MODULE, "CLASS_CREATE", ENTITY, id, scope.organizationId(), r.branchId(), Map.of("course", course.name())));
        reserveSpace(scope.organizationId(), r.branchId(), id, course.name(), r.spaceId(), r.dayOfWeek(), r.startDate(), r.endDate(), r.startTime(), r.endTime(),
                actor.ownerId());
        return get(scope, id);
    }

    /**
     * M13→M16 · El espacio siempre se puede configurar (para que quede listo cuando se contrate Instalaciones); solo se
     * valida que exista, esté ACTIVE y sea de la misma sede del dictado — mismo criterio que {@code EventService.validateSpace}
     * usa para M14→M16.
     */
    private void validateSpace(UUID orgId, UUID branchId, UUID spaceId) {
        if (spaceId == null) {
            return;
        }
        SpaceService.SpaceFacts space = spaceProvider.getObject().facts(orgId, spaceId);
        if (!"ACTIVE".equals(space.status())) {
            throw new Exceptions("error.space.inactive", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (!branchId.equals(space.branchId())) {
            throw new Exceptions("error.space.branchMismatch", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    /**
     * Genera la serie semanal de reservas del dictado si hay espacio, horario (inicio y fin) e Instalaciones (SPACES) está
     * contratado; sin cualquiera de las tres cosas, el dictado sigue igual, sin espacio reservado. Un choque puntual en una
     * sola fecha no bloquea el resto de la serie [ver ReservationService.createLinkedSeries].
     */
    private void reserveSpace(UUID orgId, UUID branchId, UUID classId, String title, UUID spaceId, Integer dayOfWeek, LocalDate startDate,
            LocalDate endDate, LocalTime startTime, LocalTime endTime, UUID requestedBy) {
        if (spaceId == null || startTime == null || endTime == null || !contractGate.enabled(orgId, FacilitySupport.MOD_SPACES)) {
            return;
        }
        java.time.ZoneId zone = support.zoneOf(branchId);
        reservationProvider.getObject().createLinkedSeries(orgId, spaceId, requestedBy, title, RESERVATION_SOURCE, classId, dayOfWeek, startDate, endDate,
                startTime, endTime, zone);
    }

    @Transactional
    public TrainingDtos.ClassResponse update(AuthenticatedActor actor, AccessScope scope, UUID id, TrainingDtos.ClassRequest r) {
        authz.require(actor, MODULE, Action.E);
        ClassRow c = lock(scope, id);
        if (!"PLANNED".equals(c.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, c.status());
        }
        if (r == null || r.startDate() == null || r.endDate() == null || r.endDate().isBefore(r.startDate())) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "fechas");
        }
        if (r.teacherId() != null && !r.teacherId().equals(c.teacherId())) {
            support.assertActiveAdult(c.orgId(), r.teacherId());
        }
        int capacity = r.capacity() == null ? c.capacity() : r.capacity();
        if (capacity < 1) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "cupo");
        }
        validateSpace(c.orgId(), c.branchId(), r.spaceId());                                                             // [M13→M16]
        int n = jdbc.update("update course_class set teacher_person_id = :t, day_of_week = :dow, start_time = :st, end_time = :et, location = :loc,"
                        + " start_date = :sd, end_date = :ed, capacity = :cap, space_id = :sp, updated_at = :at, updated_by = :by, version = version + 1"
                        + " where id = :id and (cast(:v as bigint) is null or version = :v)",
                new MapSqlParameterSource("t", r.teacherId() == null ? c.teacherId() : r.teacherId()).addValue("dow", r.dayOfWeek())
                        .addValue("st", r.startTime() == null ? null : Time.valueOf(r.startTime())).addValue("et", r.endTime() == null ? null : Time.valueOf(r.endTime()))
                        .addValue("loc", TrainingSupport.trim(r.location(), 160, "lugar")).addValue("sd", java.sql.Date.valueOf(r.startDate()))
                        .addValue("ed", java.sql.Date.valueOf(r.endDate())).addValue("cap", capacity).addValue("sp", r.spaceId()).addValue("at", Timestamp.from(clock.instant()))
                        .addValue("by", actor.ownerId()).addValue("id", id).addValue("v", r.version()));
        if (n == 0) {
            throw new Exceptions("error.common.concurrentUpdate", HttpStatus.CONFLICT);
        }
        audit.record(new AuditService.Command(MODULE, "CLASS_UPDATE", ENTITY, id, c.orgId(), c.branchId(), Map.of()));
        // [M13→M16] la clase solo se edita mientras está PLANNED (sin asistencia aún), así que resincronizar es siempre
        // "cancelar y volver a crear" — nunca deja huecos con sesiones ya dictadas, a diferencia de un evento ya publicado.
        boolean changed = !java.util.Objects.equals(c.spaceId(), r.spaceId()) || !java.util.Objects.equals(c.dayOfWeek(), r.dayOfWeek())
                || !java.util.Objects.equals(c.startTime(), r.startTime()) || !java.util.Objects.equals(c.endTime(), r.endTime())
                || !c.startDate().equals(r.startDate()) || !c.endDate().equals(r.endDate());
        if (changed) {
            if (c.spaceId() != null) {
                reservationProvider.getObject().cancelBySource(c.orgId(), RESERVATION_SOURCE, id, "Dictado actualizado");
            }
            String course = jdbc.queryForObject("select k.name from course_class t join course k on k.id = t.course_id where t.id = :id",
                    new MapSqlParameterSource("id", id), String.class);
            reserveSpace(c.orgId(), c.branchId(), id, course, r.spaceId(), r.dayOfWeek(), r.startDate(), r.endDate(), r.startTime(), r.endTime(), actor.ownerId());
        }
        return get(scope, id);
    }

    @Transactional
    public void delete(AuthenticatedActor actor, AccessScope scope, UUID id) {
        authz.require(actor, MODULE, Action.D);
        ClassRow c = lock(scope, id);
        Integer n = jdbc.queryForObject("select count(*) from enrollment where class_id = :id", new MapSqlParameterSource("id", id), Integer.class);
        if (n != null && n > 0 || !"PLANNED".equals(c.status())) {
            throw new Exceptions("error.training.hasDependencies", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (c.spaceId() != null) {
            reservationProvider.getObject().cancelBySource(c.orgId(), RESERVATION_SOURCE, id, "Dictado eliminado");
        }
        jdbc.update("delete from course_class where id = :id", new MapSqlParameterSource("id", id));
        audit.record(new AuditService.Command(MODULE, "CLASS_DELETE", ENTITY, id, c.orgId(), c.branchId(), Map.of()));
    }

    // ---------------------------------------------------------------- ciclo

    @Transactional
    public TrainingDtos.ClassResponse start(AuthenticatedActor actor, AccessScope scope, UUID id) {
        authz.require(actor, MODULE, Action.S);
        ClassRow c = lock(scope, id);
        if (!"PLANNED".equals(c.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, c.status());
        }
        jdbc.update("update course_class set status = 'IN_PROGRESS', started_at = :at, updated_at = :at, updated_by = :by, version = version + 1 where id = :id",
                new MapSqlParameterSource("at", Timestamp.from(clock.instant())).addValue("by", actor.ownerId()).addValue("id", id));
        audit.record(new AuditService.Command(MODULE, "CLASS_START", ENTITY, id, c.orgId(), c.branchId(), Map.of()));
        return get(scope, id);
    }

    /** [V12] solo si todas las matrículas están resueltas (APPROVED, FAILED o WITHDRAWN). */
    @Transactional
    public TrainingDtos.ClassResponse complete(AuthenticatedActor actor, AccessScope scope, UUID id) {
        authz.require(actor, MODULE, Action.S);
        ClassRow c = lock(scope, id);
        if (!"IN_PROGRESS".equals(c.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, c.status());
        }
        Integer unresolved = jdbc.queryForObject("select count(*) from enrollment where class_id = :id and status = 'ENROLLED'", new MapSqlParameterSource("id", id), Integer.class);
        if (unresolved != null && unresolved > 0) {
            throw new Exceptions("error.training.classUnresolved", HttpStatus.UNPROCESSABLE_ENTITY);                     // [V12]
        }
        jdbc.update("update course_class set status = 'COMPLETED', completed_at = :at, updated_at = :at, updated_by = :by, version = version + 1 where id = :id",
                new MapSqlParameterSource("at", Timestamp.from(clock.instant())).addValue("by", actor.ownerId()).addValue("id", id));
        audit.record(new AuditService.Command(MODULE, "CLASS_COMPLETE", ENTITY, id, c.orgId(), c.branchId(), Map.of()));
        return get(scope, id);
    }

    @Transactional
    public TrainingDtos.ClassResponse cancel(AuthenticatedActor actor, AccessScope scope, UUID id, String reasonText) {
        authz.require(actor, MODULE, Action.S);
        ClassRow c = lock(scope, id);
        if ("COMPLETED".equals(c.status()) || "CANCELLED".equals(c.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, c.status());
        }
        String reason = TrainingSupport.trim(reasonText, 300, "motivo");
        if (reason == null) {
            throw new Exceptions("error.common.reasonRequired", HttpStatus.BAD_REQUEST);
        }
        jdbc.update("update course_class set status = 'CANCELLED', cancel_reason = :r, updated_at = :at, updated_by = :by, version = version + 1 where id = :id",
                new MapSqlParameterSource("r", reason).addValue("at", Timestamp.from(clock.instant())).addValue("by", actor.ownerId()).addValue("id", id));
        if (c.spaceId() != null) {
            reservationProvider.getObject().cancelBySource(c.orgId(), RESERVATION_SOURCE, id, "Dictado cancelado: " + reason);
        }
        List<UUID> enrolled = jdbc.query("select person_id from enrollment where class_id = :id and status = 'ENROLLED'", new MapSqlParameterSource("id", id), (rs, i) -> (UUID) rs.getObject(1));
        if (!enrolled.isEmpty()) {
            String course = jdbc.queryForObject("select k.name from course_class t join course k on k.id = t.course_id where t.id = :id", new MapSqlParameterSource("id", id), String.class);
            notifications.toPersons(NotificationType.TRAINING_CLASS_CANCELLED, c.orgId(), enrolled, Map.of("course", course), "/app/training/classes/" + id, null);
        }
        audit.record(new AuditService.Command(MODULE, "CLASS_CANCEL", ENTITY, id, c.orgId(), c.branchId(), Map.of("reason", reason)));
        return get(scope, id);
    }

    // ---------------------------------------------------------------- asistencia (núcleo M09, contexto CLASS)

    ClassRow classRow(AccessScope scope, UUID id) {
        return load(scope, id);
    }

    int enrolledCount(UUID classId) {
        Integer n = jdbc.queryForObject("select count(*) from enrollment where class_id = :id and status <> 'WITHDRAWN'", new MapSqlParameterSource("id", classId), Integer.class);
        return n == null ? 0 : n;
    }

    /** Fechas de clase entre inicio y fin (respetando el día de la semana si está fijado), con si ya se tomó asistencia y cuántos asistieron. */
    @Transactional(readOnly = true)
    public List<TrainingDtos.SessionView> sessions(AccessScope scope, UUID classId) {
        ClassRow c = load(scope, classId);
        List<LocalDate> dates = classDates(c);
        List<TrainingDtos.SessionView> out = new ArrayList<>();
        for (LocalDate d : dates) {
            List<Object[]> s = jdbc.query("select id from attendance_session where context_type = :t and context_id = :c and session_date = :d",
                    new MapSqlParameterSource("t", CONTEXT).addValue("c", classId).addValue("d", java.sql.Date.valueOf(d)), (rs, i) -> new Object[]{rs.getObject(1)});
            if (s.isEmpty()) {
                out.add(new TrainingDtos.SessionView(d, false, 0, 0));
            } else {
                UUID sid = (UUID) s.get(0)[0];
                int present = attendance.attendeesOf(sid);
                Integer total = jdbc.queryForObject("select count(*) from attendance_record where session_id = :s", new MapSqlParameterSource("s", sid), Integer.class);
                out.add(new TrainingDtos.SessionView(d, true, present, total == null ? 0 : total));
            }
        }
        return out;
    }

    private List<LocalDate> classDates(ClassRow c) {
        List<LocalDate> out = new ArrayList<>();
        LocalDate d = c.startDate();
        if (c.dayOfWeek() != null) {
            while (d.getDayOfWeek().getValue() != c.dayOfWeek() && !d.isAfter(c.endDate())) {
                d = d.plusDays(1);
            }
        }
        while (!d.isAfter(c.endDate()) && out.size() < MAX_SESSIONS) {
            out.add(d);
            d = c.dayOfWeek() == null ? c.endDate().plusDays(1) : d.plusWeeks(1);                                        // sin patrón fijo: una sola fecha (taller)
        }
        return out;
    }

    /** Marca la asistencia de una persona en la fecha (crea la sesión CLASS de esa fecha si no existe). Solo mientras el dictado está IN_PROGRESS. */
    @Transactional
    public List<TrainingDtos.SessionView> mark(AuthenticatedActor actor, AccessScope scope, UUID classId, LocalDate date, TrainingDtos.MarkAttendanceRequest r) {
        authz.require(actor, MODULE, Action.E);
        ClassRow c = lock(scope, classId);
        if (!"IN_PROGRESS".equals(c.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, c.status());
        }
        if (date.isBefore(c.startDate()) || date.isAfter(c.endDate()) || date.isAfter(support.today(c.branchId()))) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "fecha");
        }
        if (r == null || r.personId() == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "persona");
        }
        String status = TrainingSupport.hasText(r.status()) ? r.status().trim().toUpperCase() : "PRESENT";
        if (!ATT.contains(status)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "asistencia");
        }
        String title = jdbc.queryForObject("select k.name from course_class t join course k on k.id = t.course_id where t.id = :id",
                new MapSqlParameterSource("id", classId), String.class) + " · " + date;
        UUID sid = attendance.ensureContextSession(c.orgId(), c.branchId(), CONTEXT, classId, title, date, c.startTime(), duration(c), actor.ownerId());
        attendance.record(actor, scope, sid, new AttendanceDtos.RecordRequest(r.personId(), status, "LIST"));
        return sessions(scope, classId);
    }

    private static int duration(ClassRow c) {
        if (c.startTime() == null || c.endTime() == null) {
            return 90;
        }
        return Math.max(15, (int) java.time.Duration.between(c.startTime(), c.endTime()).toMinutes());
    }

    /** Porcentaje de asistencia de una persona en el dictado (sobre las sesiones ya tomadas); 100 si aún no se tomó ninguna. */
    int attendancePct(UUID classId, UUID personId) {
        Integer total = jdbc.queryForObject("select count(distinct id) from attendance_session where context_type = :t and context_id = :c",
                new MapSqlParameterSource("t", CONTEXT).addValue("c", classId), Integer.class);
        if (total == null || total == 0) {
            return 100;
        }
        Integer present = jdbc.queryForObject("select count(*) from attendance_record r join attendance_session s on s.id = r.session_id"
                        + " where s.context_type = :t and s.context_id = :c and r.person_id = :p and r.status in ('PRESENT','LATE')",
                new MapSqlParameterSource("t", CONTEXT).addValue("c", classId).addValue("p", personId), Integer.class);
        return (int) Math.round(100.0 * (present == null ? 0 : present) / total);
    }
}
