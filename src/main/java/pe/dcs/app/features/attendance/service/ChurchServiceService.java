package pe.dcs.app.features.attendance.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.attendance.dto.AttendanceDtos;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PaginationResponse;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** M09 · Cultos (plantillas). [V2] nombre 2–80, duración 15–480, hora válida; único (sede, nombre). Los sesiones del día las crea el job o el usuario. */
@Service
@RequiredArgsConstructor
public class ChurchServiceService {

    private static final String MODULE = "ATTENDANCE";
    private static final String ENTITY = "ChurchService";
    private static final Set<String> RECURRENCES = Set.of("WEEKLY", "NONE");

    private final NamedParameterJdbcTemplate jdbc;
    private final AttendanceSupport support;
    private final AuditService audit;
    private final Clock clock;

    private static final String SELECT = "select s.id, s.branch_id, b.name as branch_name, s.name, s.service_type, ci.name_es as type_name, s.day_of_week, s.start_time, s.duration_min,"
            + " s.space_note, s.recurrence, s.self_checkin_enabled, s.status, s.version,"
            + " (select count(*) from attendance_session x where x.context_type = 'SERVICE' and x.context_id = s.id) as sessions"
            + " from church_service s join branch b on b.id = s.branch_id"
            + " left join catalog_item ci on ci.type = 'SERVICE_TYPE' and ci.code = s.service_type and (ci.organization_id is null or ci.organization_id = s.organization_id)";

    @Transactional(readOnly = true)
    public PageResponse<AttendanceDtos.ServiceResponse> search(AccessScope scope, AttendanceDtos.ServiceSearch req) {
        AttendanceDtos.ServiceSearch.Filters f = req == null || req.filters() == null ? new AttendanceDtos.ServiceSearch.Filters(null, null, null) : req.filters();
        MapSqlParameterSource ps = new MapSqlParameterSource();
        StringBuilder w = new StringBuilder(AttendanceSupport.branchScope(scope, ps, "s"));
        if (f.branchId() != null) {
            w.append(" and s.branch_id = :fb");
            ps.addValue("fb", f.branchId());
        }
        if (AttendanceSupport.hasText(f.status())) {
            String st = f.status().trim().toUpperCase();
            if (!Set.of("ACTIVE", "INACTIVE").contains(st)) {
                throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "estado");
            }
            w.append(" and s.status = :st");
            ps.addValue("st", st);
        }
        if (AttendanceSupport.hasText(f.q())) {
            w.append(" and lower(s.name) like :q");
            ps.addValue("q", "%" + f.q().trim().toLowerCase().replace("%", "").replace("_", "") + "%");
        }
        Long total = jdbc.queryForObject("select count(*) from church_service s where " + w, ps, Long.class);
        int size = req == null || req.pagination() == null ? 20 : Math.max(1, Math.min(req.pagination().getSize(), 200));
        int page = req == null || req.pagination() == null ? 0 : Math.max(0, req.pagination().getPage());
        ps.addValue("lim", size).addValue("off", (long) page * size);
        List<AttendanceDtos.ServiceResponse> rows = jdbc.query(SELECT + " where " + w + " order by b.name, s.day_of_week nulls last, s.start_time, lower(s.name) limit :lim offset :off",
                ps, (rs, i) -> map(rs));
        long t = total == null ? 0 : total;
        return new PageResponse<>(rows, new PaginationResponse((int) t, (int) Math.ceil(t / (double) size), size, page));
    }

    @Transactional(readOnly = true)
    public AttendanceDtos.ServiceResponse get(AccessScope scope, UUID id) {
        return load(scope, id);
    }

    @Transactional
    public AttendanceDtos.ServiceResponse create(AuthenticatedActor actor, AccessScope scope, AttendanceDtos.ServiceRequest r) {
        Valid v = validate(scope, r, true);
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("insert into church_service (id, organization_id, branch_id, name, service_type, day_of_week, start_time, duration_min, space_note, recurrence,"
                            + " self_checkin_enabled, status, created_at, created_by) values (:id, :o, :b, :n, :t, :d, :st, :du, :sp, :re, :sc, 'ACTIVE', :at, :by)",
                    params(id, scope, v).addValue("at", Timestamp.from(clock.instant())).addValue("by", actor.ownerId()));
        } catch (DataIntegrityViolationException e) {
            throw new Exceptions("error.attendance.serviceNameTaken", HttpStatus.CONFLICT);
        }
        audit.record(new AuditService.Command(MODULE, "CREATE", ENTITY, id, scope.organizationId(), v.branchId, diff(v)));
        return load(scope, id);
    }

    @Transactional
    public AttendanceDtos.ServiceResponse update(AuthenticatedActor actor, AccessScope scope, UUID id, AttendanceDtos.ServiceRequest r) {
        AttendanceDtos.ServiceResponse cur = load(scope, id);
        if (r != null && r.version() != null && r.version() != cur.version()) {
            throw new Exceptions("error.common.concurrentUpdate", HttpStatus.CONFLICT);
        }
        AttendanceDtos.ServiceRequest req = r == null ? null : new AttendanceDtos.ServiceRequest(cur.branchId(), r.name(), r.serviceType(), r.dayOfWeek(), r.startTime(),
                r.durationMin(), r.spaceNote(), r.recurrence(), r.selfCheckinEnabled(), r.version());
        Valid v = validate(scope, req, false);
        try {
            jdbc.update("update church_service set name = :n, service_type = :t, day_of_week = :d, start_time = :st, duration_min = :du, space_note = :sp, recurrence = :re,"
                            + " self_checkin_enabled = :sc, updated_at = :at, updated_by = :by, version = version + 1 where id = :id and organization_id = :o",
                    params(id, scope, v).addValue("at", Timestamp.from(clock.instant())).addValue("by", actor.ownerId()));
        } catch (DataIntegrityViolationException e) {
            throw new Exceptions("error.attendance.serviceNameTaken", HttpStatus.CONFLICT);
        }
        audit.record(new AuditService.Command(MODULE, "UPDATE", ENTITY, id, scope.organizationId(), cur.branchId(), diff(v)));
        return load(scope, id);
    }

    @Transactional
    public AttendanceDtos.ServiceResponse setStatus(AuthenticatedActor actor, AccessScope scope, UUID id, String status) {
        AttendanceDtos.ServiceResponse cur = load(scope, id);
        String st = status == null ? "" : status.trim().toUpperCase();
        if (!Set.of("ACTIVE", "INACTIVE").contains(st)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "estado");
        }
        if (!st.equals(cur.status())) {
            jdbc.update("update church_service set status = :s, updated_at = :at, updated_by = :by, version = version + 1 where id = :id",
                    new MapSqlParameterSource("s", st).addValue("at", Timestamp.from(clock.instant())).addValue("by", actor.ownerId()).addValue("id", id));
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("status", st);
            audit.record(new AuditService.Command(MODULE, "STATUS", ENTITY, id, scope.organizationId(), cur.branchId(), d));
        }
        return load(scope, id);
    }

    /** Solo se borra un culto que nunca tuvo sesiones; con sesiones se desactiva. */
    @Transactional
    public void delete(AccessScope scope, UUID id) {
        AttendanceDtos.ServiceResponse cur = load(scope, id);
        if (cur.sessionCount() > 0) {
            throw new Exceptions("error.attendance.hasSessions", HttpStatus.CONFLICT);
        }
        jdbc.update("delete from church_service where id = :id", new MapSqlParameterSource("id", id));
        audit.record(new AuditService.Command(MODULE, "DELETE", ENTITY, id, scope.organizationId(), cur.branchId(), Map.of("name", cur.name())));
    }

    // ---------------------------------------------------------------- internos

    private record Valid(UUID branchId, String name, String type, Integer dow, LocalTime start, int duration, String space, String recurrence, boolean self) {
    }

    private Valid validate(AccessScope scope, AttendanceDtos.ServiceRequest r, boolean creating) {
        if (r == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "datos");
        }
        UUID branch = r.branchId();
        if (creating) {
            if (branch == null) {
                throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "sede");
            }
            support.activeBranchName(scope, branch);
        }
        String name = r.name() == null ? "" : r.name().trim();
        if (name.length() < 2 || name.length() > 80) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "nombre");
        }
        if (r.durationMin() == null || r.durationMin() < 15 || r.durationMin() > 480) {
            throw new Exceptions("error.attendance.durationInvalid", HttpStatus.BAD_REQUEST);
        }
        LocalTime start;
        try {
            start = LocalTime.parse(r.startTime() == null ? "" : r.startTime().trim());
        } catch (DateTimeParseException e) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "hora");
        }
        String rec = r.recurrence() == null || r.recurrence().isBlank() ? "WEEKLY" : r.recurrence().trim().toUpperCase();
        if (!RECURRENCES.contains(rec)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "recurrencia");
        }
        Integer dow = r.dayOfWeek();
        if (dow != null && (dow < 1 || dow > 7)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "día");
        }
        if ("WEEKLY".equals(rec) && dow == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "día");
        }
        String space = AttendanceSupport.hasText(r.spaceNote()) ? r.spaceNote().trim() : null;
        if (space != null && space.length() > 80) {
            throw new Exceptions("error.common.tooLong", HttpStatus.BAD_REQUEST, "espacio", 80);
        }
        String type = support.catalogCode(scope.organizationId(), "SERVICE_TYPE", r.serviceType(), "tipo de culto");
        return new Valid(branch, name, type, dow, start, r.durationMin(), space, rec, Boolean.TRUE.equals(r.selfCheckinEnabled()));
    }

    private MapSqlParameterSource params(UUID id, AccessScope scope, Valid v) {
        return new MapSqlParameterSource("id", id).addValue("o", scope.organizationId()).addValue("b", v.branchId).addValue("n", v.name).addValue("t", v.type)
                .addValue("d", v.dow).addValue("st", java.sql.Time.valueOf(v.start)).addValue("du", v.duration).addValue("sp", v.space).addValue("re", v.recurrence)
                .addValue("sc", v.self);
    }

    private static Map<String, Object> diff(Valid v) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("name", v.name);
        d.put("type", v.type);
        d.put("dayOfWeek", v.dow);
        d.put("startTime", v.start.toString());
        d.put("durationMin", v.duration);
        d.put("recurrence", v.recurrence);
        d.put("selfCheckin", v.self);
        return d;
    }

    private AttendanceDtos.ServiceResponse load(AccessScope scope, UUID id) {
        MapSqlParameterSource ps = new MapSqlParameterSource("id", id);
        String w = AttendanceSupport.branchScope(scope, ps, "s");
        return jdbc.query(SELECT + " where s.id = :id and " + w, ps, (rs, i) -> map(rs)).stream().findFirst()
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    private static AttendanceDtos.ServiceResponse map(java.sql.ResultSet rs) throws java.sql.SQLException {
        int dow = rs.getInt("day_of_week");
        Integer dowN = rs.wasNull() ? null : dow;
        return new AttendanceDtos.ServiceResponse((UUID) rs.getObject("id"), (UUID) rs.getObject("branch_id"), rs.getString("branch_name"), rs.getString("name"),
                rs.getString("service_type"), rs.getString("type_name"), dowN, rs.getTime("start_time").toLocalTime().toString(), rs.getInt("duration_min"),
                rs.getString("space_note"), rs.getString("recurrence"), rs.getBoolean("self_checkin_enabled"), rs.getString("status"), rs.getInt("sessions"), rs.getLong("version"));
    }
}
