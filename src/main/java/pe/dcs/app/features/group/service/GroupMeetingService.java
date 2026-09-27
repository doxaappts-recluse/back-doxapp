package pe.dcs.app.features.group.service;

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
import pe.dcs.app.features.group.dto.GroupDtos;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.AuthorizationService;
import pe.dcs.app.security.authz.ContractGate;
import pe.dcs.app.shared.audit.AuditService;
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
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * M10 · Reuniones del grupo, con la asistencia del núcleo M09 (contexto GROUP_MEETING). PLANNED → HELD | CANCELLED.
 * [V8] la fecha no es anterior al inicio del grupo; una reunión se marca HELD solo si su fecha ya llegó y con asistencia (≥ 1 presente) o el motivo por el que no hubo.
 */
@Service
@RequiredArgsConstructor
public class GroupMeetingService {

    private static final String MODULE = GroupSupport.MODULE;
    private static final String ENTITY = "GroupMeeting";
    private static final String CONTEXT = "GROUP_MEETING";
    private static final Set<String> STATUSES = Set.of("PLANNED", "HELD", "CANCELLED");
    private static final Set<String> ATT = Set.of("PRESENT", "LATE", "EXCUSED", "ABSENT");
    private static final int MAX_REPEAT = 26;
    /** [M10→M16] mismo valor que GroupSupport.MODULE: ReservationService.submit() ya contemplaba 'SMALL_GROUP' como sourceType. */
    private static final String RESERVATION_SOURCE = GroupSupport.MODULE;

    private final NamedParameterJdbcTemplate jdbc;
    private final GroupSupport support;
    private final AttendanceService attendance;
    private final AuthorizationService authz;
    private final AuditService audit;
    private final Clock clock;
    private final ContractGate contractGate;
    private final ObjectProvider<SpaceService> spaceProvider;
    private final ObjectProvider<ReservationService> reservationProvider;

    private record Meeting(UUID id, UUID groupId, UUID branchId, LocalDate date, LocalTime time, String status, UUID sessionId, long version, LocalTime endTime, UUID spaceId) {
    }

    private static final String SELECT = "select t.*, g.name as group_name, sp.name as space_name,"
            + " (select count(*) from attendance_record r where r.session_id = t.attendance_session_id and r.status in ('PRESENT','LATE')) as attendees,"
            + " (select count(*) from group_member m where m.group_id = t.group_id and m.status = 'ACTIVE') as members"
            + " from group_meeting t join small_group g on g.id = t.group_id left join space sp on sp.id = t.space_id";

    private static GroupDtos.MeetingResponse map(ResultSet rs) throws SQLException {
        Time tm = rs.getTime("meeting_time");
        Time et = rs.getTime("end_time");
        return new GroupDtos.MeetingResponse((UUID) rs.getObject("id"), (UUID) rs.getObject("group_id"), rs.getString("group_name"), (UUID) rs.getObject("branch_id"),
                rs.getDate("meeting_date").toLocalDate(), tm == null ? null : tm.toLocalTime(), rs.getString("topic"), rs.getString("location"), rs.getString("status"),
                rs.getString("notes"), rs.getString("no_attendance_reason"), rs.getString("cancel_reason"), (UUID) rs.getObject("attendance_session_id"), rs.getInt("attendees"),
                rs.getInt("members"), rs.getLong("version"), et == null ? null : et.toLocalTime(), (UUID) rs.getObject("space_id"), rs.getString("space_name"));
    }

    // ---------------------------------------------------------------- consulta

    @Transactional(readOnly = true)
    public PageResponse<GroupDtos.MeetingResponse> search(AccessScope scope, GroupDtos.MeetingSearch req) {
        MapSqlParameterSource ps = new MapSqlParameterSource();
        StringBuilder w = new StringBuilder(GroupSupport.visible(scope, ps, "g"));
        if (req != null && req.groupId() != null) {
            w.append(" and t.group_id = :gid");
            ps.addValue("gid", req.groupId());
        }
        if (req != null && GroupSupport.hasText(req.status())) {
            String st = req.status().trim().toUpperCase();
            if (!STATUSES.contains(st)) {
                throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "estado");
            }
            w.append(" and t.status = :st");
            ps.addValue("st", st);
        }
        if (req != null && req.from() != null) {
            w.append(" and t.meeting_date >= :df");
            ps.addValue("df", java.sql.Date.valueOf(req.from()));
        }
        if (req != null && req.to() != null) {
            w.append(" and t.meeting_date <= :dt");
            ps.addValue("dt", java.sql.Date.valueOf(req.to()));
        }
        Long total = jdbc.queryForObject("select count(*) from group_meeting t join small_group g on g.id = t.group_id where " + w, ps, Long.class);
        int size = req == null || req.pagination() == null ? 50 : Math.max(1, Math.min(req.pagination().getSize(), 200));
        int page = req == null || req.pagination() == null ? 0 : Math.max(0, req.pagination().getPage());
        ps.addValue("lim", size).addValue("off", (long) page * size);
        boolean asc = req != null && req.from() != null;
        List<GroupDtos.MeetingResponse> rows = jdbc.query(SELECT + " where " + w + " order by t.meeting_date " + (asc ? "asc" : "desc") + ", t.meeting_time limit :lim offset :off", ps, (rs, i) -> map(rs));
        long t = total == null ? 0 : total;
        return new PageResponse<>(rows, new PaginationResponse((int) t, (int) Math.ceil(t / (double) size), size, page));
    }

    @Transactional(readOnly = true)
    public GroupDtos.MeetingResponse get(AccessScope scope, UUID groupId, UUID id) {
        support.load(scope, groupId);
        return byId(groupId, id);
    }

    private GroupDtos.MeetingResponse byId(UUID groupId, UUID id) {
        return jdbc.query(SELECT + " where t.id = :id and t.group_id = :g", new MapSqlParameterSource("id", id).addValue("g", groupId), (rs, i) -> map(rs)).stream().findFirst()
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    private Meeting lock(UUID groupId, UUID id) {
        return jdbc.query("select t.* from group_meeting t where t.id = :id and t.group_id = :g for update", new MapSqlParameterSource("id", id).addValue("g", groupId), (rs, i) -> {
            Time tm = rs.getTime("meeting_time");
            Time et = rs.getTime("end_time");
            return new Meeting((UUID) rs.getObject("id"), (UUID) rs.getObject("group_id"), (UUID) rs.getObject("branch_id"), rs.getDate("meeting_date").toLocalDate(),
                    tm == null ? null : tm.toLocalTime(), rs.getString("status"), (UUID) rs.getObject("attendance_session_id"), rs.getLong("version"),
                    et == null ? null : et.toLocalTime(), (UUID) rs.getObject("space_id"));
        }).stream().findFirst().orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    /** [M10→M16] el espacio debe pertenecer a la misma sede del grupo y estar ACTIVE — mismo criterio que EventService/CourseClassService. */
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
     * [M10→M16] intenta reservar una sola franja para una reunión puntual (una fila = una ocurrencia, a diferencia de la serie
     * de M13). Si SPACES no está contratado, o falta la hora de fin, no reserva nada (el espacio queda solo configurado, listo
     * para cuando se contrate). Reusa {@code createLinkedSeries(...)} con {@code dayOfWeek = null} y {@code startDate = endDate
     * = date} — exactamente su caso de "una sola ocurrencia tipo taller" — en vez de {@code createLinked()}, a propósito:
     * {@code createLinked()} lanza {@code error.reservation.overlap} si la franja ya está ocupada, y como aquí ya hay una
     * transacción activa (la de {@code create()}/{@code update()}), Spring marca esa transacción como rollback-only en cuanto
     * {@code createLinked()} lanza la excepción — atraparla en este método NO evita el `UnexpectedRollbackException` posterior
     * al intentar hacer commit (se comprobó en vivo). {@code createLinkedSeries()} en cambio comprueba el choque ANTES de
     * insertar y nunca lanza, que es justo el criterio de "no bloquear por un choque puntual" que esta integración necesita.
     */
    private void reserveSpace(UUID orgId, UUID branchId, UUID meetingId, String title, UUID spaceId, LocalDate date, LocalTime startTime, LocalTime endTime, UUID requestedBy) {
        if (spaceId == null || startTime == null || endTime == null || !contractGate.enabled(orgId, FacilitySupport.MOD_SPACES)) {
            return;
        }
        ZoneId zone = support.zoneOf(branchId);
        reservationProvider.getObject().createLinkedSeries(orgId, spaceId, requestedBy, title, RESERVATION_SOURCE, meetingId, null, date, date, startTime, endTime, zone);
    }

    // ---------------------------------------------------------------- alta y edición

    @Transactional
    public GroupDtos.MeetingCreated create(AuthenticatedActor actor, AccessScope scope, UUID groupId, GroupDtos.MeetingRequest r) {
        authz.require(actor, MODULE, Action.E);
        GroupSupport.GroupRow g = support.lock(scope, groupId);
        if (!"ACTIVE".equals(g.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, g.status());
        }
        if (r == null || r.date() == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "fecha");
        }
        LocalDate today = support.today(g.branchId());
        checkDate(g, r.date(), today);
        int repeat = r.repeatWeeks() == null ? 0 : r.repeatWeeks();
        if (repeat < 0 || repeat > MAX_REPEAT) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "repeticiones");
        }
        validateSpace(g.orgId(), g.branchId(), r.spaceId());
        Map<String, Object> defaults = jdbc.queryForMap("select meeting_time, location from small_group where id = :g", new MapSqlParameterSource("g", groupId));
        Time time = r.time() != null ? Time.valueOf(r.time()) : (Time) defaults.get("meeting_time");
        LocalTime startTime = time == null ? null : time.toLocalTime();
        String location = GroupSupport.hasText(r.location()) ? GroupSupport.trim(r.location(), 160, "ubicación") : (String) defaults.get("location");
        String topic = GroupSupport.trim(r.topic(), 160, "tema");
        UUID series = repeat > 0 ? UUID.randomUUID() : null;
        List<UUID> made = new ArrayList<>();
        int skipped = 0;
        for (int i = 0; i <= repeat; i++) {
            LocalDate d = r.date().plusWeeks(i);
            if (taken(groupId, d, null)) {
                skipped++;
                continue;
            }
            UUID id = UUID.randomUUID();
            jdbc.update("insert into group_meeting (id, organization_id, branch_id, group_id, meeting_date, meeting_time, end_time, space_id, topic, location, status, series_id, created_at, created_by)"
                            + " values (:id, :o, :b, :g, :d, :t, :et, :sp, :tp, :l, 'PLANNED', :s, :at, :by)",
                    new MapSqlParameterSource("id", id).addValue("o", g.orgId()).addValue("b", g.branchId()).addValue("g", groupId).addValue("d", java.sql.Date.valueOf(d)).addValue("t", time)
                            .addValue("et", r.endTime() == null ? null : Time.valueOf(r.endTime())).addValue("sp", r.spaceId())
                            .addValue("tp", topic).addValue("l", location).addValue("s", series).addValue("at", Timestamp.from(clock.instant())).addValue("by", actor.ownerId()));
            made.add(id);
            reserveSpace(g.orgId(), g.branchId(), id, g.name(), r.spaceId(), d, startTime, r.endTime(), actor.ownerId());
        }
        if (made.isEmpty()) {
            throw new Exceptions("error.group.meetingExists", HttpStatus.CONFLICT);
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("group", g.name());
        d.put("first", r.date().toString());
        d.put("created", made.size());
        audit.record(new AuditService.Command(MODULE, "MEETING_CREATE", ENTITY, made.get(0), g.orgId(), g.branchId(), d));
        List<GroupDtos.MeetingResponse> out = new ArrayList<>();
        for (UUID id : made) {
            out.add(byId(groupId, id));
        }
        return new GroupDtos.MeetingCreated(made.size(), skipped, out);
    }

    private void checkDate(GroupSupport.GroupRow g, LocalDate date, LocalDate today) {
        if (g.startDate() != null && date.isBefore(g.startDate())) {
            throw new Exceptions("error.group.meetingBeforeStart", HttpStatus.UNPROCESSABLE_ENTITY);                                  // [V8]
        }
        if (date.isBefore(today.minusDays(60)) || date.isAfter(today.plusDays(365))) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "fecha");
        }
    }

    private boolean taken(UUID groupId, LocalDate d, UUID except) {
        Integer n = jdbc.queryForObject("select count(*) from group_meeting where group_id = :g and meeting_date = :d and status <> 'CANCELLED' and (cast(:x as uuid) is null or id <> :x)",
                new MapSqlParameterSource("g", groupId).addValue("d", java.sql.Date.valueOf(d)).addValue("x", except), Integer.class);
        return n != null && n > 0;
    }

    @Transactional
    public GroupDtos.MeetingResponse update(AuthenticatedActor actor, AccessScope scope, UUID groupId, UUID id, GroupDtos.MeetingUpdate r) {
        authz.require(actor, MODULE, Action.E);
        GroupSupport.GroupRow g = support.load(scope, groupId);
        Meeting m = lock(groupId, id);
        if (r == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "datos");
        }
        if (!"PLANNED".equals(m.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, m.status());
        }
        LocalDate date = r.date() == null ? m.date() : r.date();
        if (!date.equals(m.date())) {
            checkDate(g, date, support.today(g.branchId()));
            if (taken(groupId, date, id)) {
                throw new Exceptions("error.group.meetingExists", HttpStatus.CONFLICT);
            }
        }
        LocalTime startTime = r.time() == null ? m.time() : r.time();
        validateSpace(g.orgId(), g.branchId(), r.spaceId());
        int n = jdbc.update("update group_meeting set meeting_date = :d, meeting_time = :t, end_time = :et, space_id = :sp, topic = :tp, location = :l, updated_at = :at, updated_by = :by, version = version + 1"
                        + " where id = :id and (cast(:v as bigint) is null or version = :v)",
                new MapSqlParameterSource("d", java.sql.Date.valueOf(date)).addValue("t", startTime == null ? null : Time.valueOf(startTime))
                        .addValue("et", r.endTime() == null ? null : Time.valueOf(r.endTime())).addValue("sp", r.spaceId())
                        .addValue("tp", GroupSupport.trim(r.topic(), 160, "tema")).addValue("l", GroupSupport.trim(r.location(), 160, "ubicación"))
                        .addValue("at", Timestamp.from(clock.instant())).addValue("by", actor.ownerId()).addValue("id", id).addValue("v", r.version()));
        if (n == 0) {
            throw new Exceptions("error.common.concurrentUpdate", HttpStatus.CONFLICT);
        }
        // [M10→M16] si cambió la fecha, la hora de inicio, la hora de fin o el espacio, la reserva anterior (si había) ya no
        // corresponde: se cancela y, si sigue habiendo espacio + hora de fin, se genera una nueva para los datos actualizados.
        boolean changed = !date.equals(m.date()) || !java.util.Objects.equals(startTime, m.time()) || !java.util.Objects.equals(r.endTime(), m.endTime())
                || !java.util.Objects.equals(r.spaceId(), m.spaceId());
        if (changed) {
            if (m.spaceId() != null) {
                reservationProvider.getObject().cancelBySource(g.orgId(), RESERVATION_SOURCE, id, "Reunión reprogramada");
            }
            if (r.spaceId() != null) {
                reserveSpace(g.orgId(), g.branchId(), id, g.name(), r.spaceId(), date, startTime, r.endTime(), actor.ownerId());
            }
        }
        audit.record(new AuditService.Command(MODULE, "MEETING_UPDATE", ENTITY, id, g.orgId(), g.branchId(), Map.of("date", date.toString())));
        return byId(groupId, id);
    }

    @Transactional
    public GroupDtos.MeetingResponse cancel(AuthenticatedActor actor, AccessScope scope, UUID groupId, UUID id, String reason) {
        authz.require(actor, MODULE, Action.E);
        GroupSupport.GroupRow g = support.load(scope, groupId);
        Meeting m = lock(groupId, id);
        if (!"PLANNED".equals(m.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, m.status());
        }
        String why = reason == null ? "" : reason.trim();
        if (why.length() < 3) {
            throw new Exceptions("error.common.reasonRequired", HttpStatus.BAD_REQUEST);
        }
        if (why.length() > 200) {
            throw new Exceptions("error.common.tooLong", HttpStatus.BAD_REQUEST, "motivo", 200);
        }
        if (m.sessionId() != null) {
            if (attendance.attendeesOf(m.sessionId()) > 0) {
                throw new Exceptions("error.group.meetingHasAttendance", HttpStatus.CONFLICT);
            }
            attendance.closeContextSession(m.sessionId(), actor.ownerId());
        }
        jdbc.update("update group_meeting set status = 'CANCELLED', cancel_reason = :r, updated_at = :at, updated_by = :by, version = version + 1 where id = :id",
                new MapSqlParameterSource("r", why).addValue("at", Timestamp.from(clock.instant())).addValue("by", actor.ownerId()).addValue("id", id));
        if (m.spaceId() != null) {
            reservationProvider.getObject().cancelBySource(g.orgId(), RESERVATION_SOURCE, id, "Reunión cancelada: " + why);
        }
        audit.record(new AuditService.Command(MODULE, "MEETING_CANCEL", ENTITY, id, g.orgId(), g.branchId(), Map.of("reason", why)));
        return byId(groupId, id);
    }

    // ---------------------------------------------------------------- asistencia

    @Transactional(readOnly = true)
    public GroupDtos.AttendanceView attendance(AccessScope scope, UUID groupId, UUID id) {
        GroupSupport.GroupRow g = support.load(scope, groupId);
        GroupDtos.MeetingResponse m = byId(groupId, id);
        LocalDate today = support.today(g.branchId());
        UUID sid = m.attendanceSessionId() == null ? new UUID(0, 0) : m.attendanceSessionId();
        MapSqlParameterSource ps = new MapSqlParameterSource("s", sid).addValue("g", groupId);
        List<GroupDtos.AttendanceRow> rows = jdbc.query("select * from (select p.id as pid, trim(p.first_name || ' ' || p.last_name) as pname, m.role as prole, r.status as pstatus, false as guest, p.birth_date as birth,"
                + " p.last_name as ln, p.first_name as fn from group_member m join person p on p.id = m.person_id left join attendance_record r on r.session_id = :s and r.person_id = m.person_id"
                + " where m.group_id = :g and m.status = 'ACTIVE'"
                + " union all select p.id, trim(p.first_name || ' ' || p.last_name), null, r.status, true, p.birth_date, p.last_name, p.first_name from attendance_record r"
                + " join person p on p.id = r.person_id where r.session_id = :s and not exists (select 1 from group_member x where x.group_id = :g and x.person_id = r.person_id and x.status = 'ACTIVE')) u"
                + " order by u.guest, lower(u.ln), lower(u.fn)", ps, (rs, i) -> {
            java.sql.Date b = rs.getDate("birth");
            return new GroupDtos.AttendanceRow((UUID) rs.getObject("pid"), rs.getString("pname"), rs.getString("prole"), rs.getString("pstatus"), rs.getBoolean("guest"),
                    b != null && GroupSupport.minor(b.toLocalDate(), today));
        });
        boolean canMark = "PLANNED".equals(m.status()) && !m.date().isAfter(today) && "ACTIVE".equals(g.status());
        return new GroupDtos.AttendanceView(m, rows, m.attendees(), canMark);
    }

    @Transactional
    public GroupDtos.AttendanceView mark(AuthenticatedActor actor, AccessScope scope, UUID groupId, UUID id, GroupDtos.AttendanceMark r) {
        authz.require(actor, MODULE, Action.E);
        GroupSupport.GroupRow g = support.load(scope, groupId);
        Meeting m = openForAttendance(g, groupId, id);
        if (r == null || r.personId() == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "persona");
        }
        String status = r.status() == null || r.status().isBlank() ? "PRESENT" : r.status().trim().toUpperCase();
        if (!ATT.contains(status)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "asistencia");
        }
        UUID sid = m.sessionId();
        if (sid == null) {
            sid = attendance.ensureContextSession(g.orgId(), g.branchId(), CONTEXT, id, g.name() + " · " + m.date(), m.date(), m.time(), 120, actor.ownerId());
            jdbc.update("update group_meeting set attendance_session_id = :s, updated_at = :at, version = version + 1 where id = :id",
                    new MapSqlParameterSource("s", sid).addValue("at", Timestamp.from(clock.instant())).addValue("id", id));
        }
        attendance.record(actor, scope, sid, new AttendanceDtos.RecordRequest(r.personId(), status, "LIST"));
        return attendance(scope, groupId, id);
    }

    @Transactional
    public GroupDtos.AttendanceView unmark(AuthenticatedActor actor, AccessScope scope, UUID groupId, UUID id, UUID personId) {
        authz.require(actor, MODULE, Action.E);
        GroupSupport.GroupRow g = support.load(scope, groupId);
        Meeting m = openForAttendance(g, groupId, id);
        if (m.sessionId() == null) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        attendance.removeRecord(scope, m.sessionId(), personId);
        return attendance(scope, groupId, id);
    }

    private Meeting openForAttendance(GroupSupport.GroupRow g, UUID groupId, UUID id) {
        Meeting m = lock(groupId, id);
        if (!"PLANNED".equals(m.status()) || !"ACTIVE".equals(g.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, "PLANNED".equals(m.status()) ? g.status() : m.status());
        }
        if (m.date().isAfter(support.today(g.branchId()))) {
            throw new Exceptions("error.group.meetingFuture", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        return m;
    }

    /** [V8] Marca la reunión como celebrada: fecha ≤ hoy y, o hay al menos un asistente, o se indica por qué no hubo. Cierra la sesión de asistencia. */
    @Transactional
    public GroupDtos.MeetingResponse hold(AuthenticatedActor actor, AccessScope scope, UUID groupId, UUID id, GroupDtos.HoldRequest r) {
        authz.require(actor, MODULE, Action.E);
        GroupSupport.GroupRow g = support.load(scope, groupId);
        Meeting m = openForAttendance(g, groupId, id);
        int attendees = m.sessionId() == null ? 0 : attendance.attendeesOf(m.sessionId());
        String reason = GroupSupport.trim(r == null ? null : r.noAttendanceReason(), 200, "motivo");
        if (attendees == 0 && (reason == null || reason.length() < 3)) {
            throw new Exceptions("error.group.meetingNoAttendance", HttpStatus.UNPROCESSABLE_ENTITY);                                // [V8]
        }
        if (m.sessionId() != null) {
            attendance.closeContextSession(m.sessionId(), actor.ownerId());
        }
        jdbc.update("update group_meeting set status = 'HELD', held_at = :at, notes = :n, topic = coalesce(:tp, topic), no_attendance_reason = :nr, updated_at = :at, updated_by = :by,"
                        + " version = version + 1 where id = :id",
                new MapSqlParameterSource("at", Timestamp.from(clock.instant())).addValue("n", GroupSupport.trim(r == null ? null : r.notes(), 1000, "notas"))
                        .addValue("tp", GroupSupport.trim(r == null ? null : r.topic(), 160, "tema")).addValue("nr", attendees == 0 ? reason : null).addValue("by", actor.ownerId())
                        .addValue("id", id));
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("attendees", attendees);
        d.put("date", m.date().toString());
        audit.record(new AuditService.Command(MODULE, "MEETING_HELD", ENTITY, id, g.orgId(), g.branchId(), d));
        return byId(groupId, id);
    }
}
