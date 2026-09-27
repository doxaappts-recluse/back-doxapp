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
import pe.dcs.app.shared.export.XlsxWriter;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.RoleType;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PaginationResponse;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
 * M09 · Núcleo de asistencia (01 §2): sesiones (contextType SERVICE | EVENT | GROUP_MEETING | CLASS | SHIFT) y registros únicos por (sesión, persona).
 * Ciclo PLANNED → OPEN (desde 2 h antes) → CLOSED; reabrir con la acción A y motivo dentro de {@code reopenDays}.
 * [V3] una sesión por (culto, fecha) · [V4] registrar solo con la sesión OPEN · [V5] repetir es idempotente · [V6] cerrada bloquea la edición ·
 * [V7] visitantes anónimos ≥ 0 · [V12] no se registra a una persona que no esté activa.
 */
@Service
@RequiredArgsConstructor
public class AttendanceService {

    static final String MODULE = "ATTENDANCE";
    private static final String ENTITY = "AttendanceSession";
    private static final Duration OPEN_BEFORE = Duration.ofHours(2);
    private static final Set<String> STATUSES = Set.of("PRESENT", "LATE", "EXCUSED", "ABSENT");
    private static final Set<String> METHODS = Set.of("MANUAL", "LIST", "QR", "SELF");
    private static final Set<String> SESSION_STATUS = Set.of("PLANNED", "OPEN", "CLOSED");
    private static final int MAX_RECORDS = 3000;

    private final NamedParameterJdbcTemplate jdbc;
    private final AttendanceSupport support;
    private final AttendanceRulesService rules;
    private final AuditService audit;
    private final Clock clock;

    /** Sesión cargada dentro del alcance. */
    record Row(UUID id, UUID orgId, UUID branchId, String contextType, UUID contextId, String title, LocalDate date, Instant startsAt, Instant endsAt,
               String status, int anonymousCount, boolean selfCheckin, Instant openedAt, Instant closedAt, int reopenCount, String lastReopenReason, long version) {
    }

    private static final String SUMMARY = "select s.*, b.name as branch_name,"
            + " (select count(*) from attendance_record r where r.session_id = s.id and r.status in ('PRESENT','LATE')) as attendees,"
            + " (select count(*) from attendance_record r where r.session_id = s.id and r.status = 'EXCUSED') as excused,"
            + " (select count(*) from child_checkin c where c.session_id = s.id) as children"
            + " from attendance_session s join branch b on b.id = s.branch_id";

    // ---------------------------------------------------------------- consulta

    @Transactional(readOnly = true)
    public PageResponse<AttendanceDtos.SessionSummary> search(AccessScope scope, AttendanceDtos.SessionSearch req) {
        AttendanceDtos.SessionSearch.Filters f = req == null || req.filters() == null ? new AttendanceDtos.SessionSearch.Filters(null, null, null, null, null) : req.filters();
        MapSqlParameterSource ps = new MapSqlParameterSource();
        StringBuilder w = new StringBuilder(visible(scope, ps));
        if (f.branchId() != null) {
            w.append(" and s.branch_id = :fb");
            ps.addValue("fb", f.branchId());
        }
        if (f.serviceId() != null) {
            w.append(" and s.context_type = 'SERVICE' and s.context_id = :fs");
            ps.addValue("fs", f.serviceId());
        }
        if (AttendanceSupport.hasText(f.status())) {
            String st = f.status().trim().toUpperCase();
            if (!SESSION_STATUS.contains(st)) {
                throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "estado");
            }
            w.append(" and s.status = :st");
            ps.addValue("st", st);
        }
        if (f.from() != null) {
            w.append(" and s.session_date >= :df");
            ps.addValue("df", java.sql.Date.valueOf(f.from()));
        }
        if (f.to() != null) {
            w.append(" and s.session_date <= :dt");
            ps.addValue("dt", java.sql.Date.valueOf(f.to()));
        }
        Long total = jdbc.queryForObject("select count(*) from attendance_session s where " + w, ps, Long.class);
        int size = req == null || req.pagination() == null ? 20 : Math.max(1, Math.min(req.pagination().getSize(), 200));
        int page = req == null || req.pagination() == null ? 0 : Math.max(0, req.pagination().getPage());
        ps.addValue("lim", size).addValue("off", (long) page * size);
        List<AttendanceDtos.SessionSummary> rows = jdbc.query(SUMMARY + " where " + w + " order by s.session_date desc, s.starts_at desc, b.name limit :lim offset :off", ps, (rs, i) -> {
            int att = rs.getInt("attendees");
            int anon = rs.getInt("anonymous_count");
            return new AttendanceDtos.SessionSummary((UUID) rs.getObject("id"), (UUID) rs.getObject("branch_id"), rs.getString("branch_name"), rs.getString("context_type"),
                    (UUID) rs.getObject("context_id"), rs.getString("title"), rs.getDate("session_date").toLocalDate(), rs.getTimestamp("starts_at").toInstant(),
                    rs.getTimestamp("ends_at").toInstant(), rs.getString("status"), att, rs.getInt("excused"), anon, att + anon, rs.getBoolean("self_checkin"), rs.getInt("children"));
        });
        long t = total == null ? 0 : total;
        return new PageResponse<>(rows, new PaginationResponse((int) t, (int) Math.ceil(t / (double) size), size, page));
    }

    @Transactional(readOnly = true)
    public AttendanceDtos.SessionResponse get(AccessScope scope, UUID id) {
        return toResponse(load(scope, id));
    }

    // ---------------------------------------------------------------- ciclo de la sesión

    /** Crea a mano la sesión de un culto en una fecha [V3]. */
    @Transactional
    public AttendanceDtos.SessionResponse create(AuthenticatedActor actor, AccessScope scope, AttendanceDtos.SessionCreate r) {
        if (r == null || r.serviceId() == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "culto");
        }
        LocalDate date = r.date() == null ? LocalDate.now(clock) : r.date();
        LocalDate today = LocalDate.now(clock);
        if (date.isBefore(today.minusDays(365)) || date.isAfter(today.plusDays(90))) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "fecha");
        }
        MapSqlParameterSource ps = new MapSqlParameterSource("id", r.serviceId());
        String w = AttendanceSupport.branchScope(scope, ps, "s");
        List<Object[]> svc = jdbc.query("select s.branch_id, s.name, s.start_time, s.duration_min, s.self_checkin_enabled, s.status from church_service s where s.id = :id and " + w, ps,
                (rs, i) -> new Object[]{rs.getObject(1), rs.getString(2), rs.getTime(3).toLocalTime(), rs.getInt(4), rs.getBoolean(5), rs.getString(6)});
        if (svc.isEmpty()) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        Object[] s = svc.get(0);
        if (!"ACTIVE".equals(s[5])) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, s[5]);
        }
        UUID id = insertSession(scope.organizationId(), (UUID) s[0], r.serviceId(), (String) s[1], (LocalTime) s[2], (int) s[3], (boolean) s[4], date, "MANUAL", actor.ownerId());
        if (id == null) {
            throw new Exceptions("error.attendance.sessionExists", HttpStatus.CONFLICT);
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("service", s[1]);
        d.put("date", date.toString());
        audit.record(new AuditService.Command(MODULE, "SESSION_CREATE", ENTITY, id, scope.organizationId(), (UUID) s[0], d));
        return toResponse(load(scope, id));
    }

    /** Inserta la sesión de un culto en esa fecha (hora local de la sede); null si ya existía. Lo usa también el job. */
    UUID insertSession(UUID orgId, UUID branchId, UUID serviceId, String title, LocalTime start, int durationMin, boolean self, LocalDate date, String origin, UUID by) {
        ZoneId zone = support.zoneOf(branchId);
        Instant from = date.atTime(start).atZone(zone).toInstant();
        UUID id = UUID.randomUUID();
        int n = jdbc.update("insert into attendance_session (id, organization_id, branch_id, context_type, context_id, title, session_date, starts_at, ends_at, status, origin,"
                        + " self_checkin, created_at, created_by) values (:id, :o, :b, 'SERVICE', :c, :t, :d, :sa, :ea, 'PLANNED', :or, :sc, :at, :by)"
                        + " on conflict (context_type, context_id, session_date) where context_id is not null do nothing",
                new MapSqlParameterSource("id", id).addValue("o", orgId).addValue("b", branchId).addValue("c", serviceId).addValue("t", title)
                        .addValue("d", java.sql.Date.valueOf(date)).addValue("sa", Timestamp.from(from)).addValue("ea", Timestamp.from(from.plus(Duration.ofMinutes(durationMin))))
                        .addValue("or", origin).addValue("sc", self).addValue("at", Timestamp.from(clock.instant())).addValue("by", by));
        return n == 0 ? null : id;
    }

    /**
     * Núcleo compartido (M10 reuniones de grupo, luego M13/M14/M11): devuelve la sesión del contexto en esa fecha y, si no existe, la crea ya ABIERTA.
     * Quien la llama valida antes que el contexto sea del alcance del actor y que la fecha ya llegó; aquí no se aplica la regla de las 2 horas.
     */
    @Transactional
    public UUID ensureContextSession(UUID orgId, UUID branchId, String contextType, UUID contextId, String title, LocalDate date, LocalTime start, int durationMin, UUID by) {
        List<UUID> cur = jdbc.query("select id from attendance_session where context_type = :t and context_id = :c and session_date = :d",
                new MapSqlParameterSource("t", contextType).addValue("c", contextId).addValue("d", java.sql.Date.valueOf(date)), (rs, i) -> (UUID) rs.getObject(1));
        if (!cur.isEmpty()) {
            return cur.get(0);
        }
        ZoneId zone = support.zoneOf(branchId);
        Instant from = date.atTime(start == null ? LocalTime.of(19, 0) : start).atZone(zone).toInstant();
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update("insert into attendance_session (id, organization_id, branch_id, context_type, context_id, title, session_date, starts_at, ends_at, status, origin,"
                        + " self_checkin, opened_at, opened_by, created_at, created_by) values (:id, :o, :b, :ct, :c, :t, :d, :sa, :ea, 'OPEN', 'MANUAL', false, :at, :by, :at, :by)",
                new MapSqlParameterSource("id", id).addValue("o", orgId).addValue("b", branchId).addValue("ct", contextType).addValue("c", contextId).addValue("t", title)
                        .addValue("d", java.sql.Date.valueOf(date)).addValue("sa", Timestamp.from(from)).addValue("ea", Timestamp.from(from.plus(Duration.ofMinutes(Math.max(15, durationMin)))))
                        .addValue("at", Timestamp.from(now)).addValue("by", by));
        audit.record(new AuditService.Command(MODULE, "SESSION_CREATE", ENTITY, id, orgId, branchId, Map.of("context", contextType, "date", date.toString())));
        return id;
    }

    /** Cierra la sesión de un contexto externo (quien llama ya validó el alcance); devuelve cuántas personas asistieron. */
    @Transactional
    public int closeContextSession(UUID sessionId, UUID by) {
        Row s = loadRaw(sessionId);
        if (!"OPEN".equals(s.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, s.status());
        }
        jdbc.update("update attendance_session set status = 'CLOSED', closed_at = :at, closed_by = :by, version = version + 1 where id = :id and status = 'OPEN'",
                new MapSqlParameterSource("id", sessionId).addValue("at", Timestamp.from(clock.instant())).addValue("by", by));
        int att = attendeesOf(sessionId);
        audit.record(new AuditService.Command(MODULE, "SESSION_CLOSE", ENTITY, sessionId, s.orgId(), s.branchId(), Map.of("attendees", att, "anonymous", s.anonymousCount())));
        return att;
    }

    /** Cuántas personas figuran como presentes o tarde en la sesión. */
    @Transactional(readOnly = true)
    public int attendeesOf(UUID sessionId) {
        Integer n = jdbc.queryForObject("select count(*) from attendance_record where session_id = :s and status in ('PRESENT','LATE')", new MapSqlParameterSource("s", sessionId), Integer.class);
        return n == null ? 0 : n;
    }

    @Transactional
    public AttendanceDtos.SessionResponse open(AuthenticatedActor actor, AccessScope scope, UUID id) {
        Row s = load(scope, id);
        if (!"PLANNED".equals(s.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, s.status());
        }
        Instant now = clock.instant();
        if (now.isBefore(s.startsAt().minus(OPEN_BEFORE))) {
            throw new Exceptions("error.attendance.tooEarly", HttpStatus.UNPROCESSABLE_ENTITY, 2);
        }
        int n = jdbc.update("update attendance_session set status = 'OPEN', opened_at = :at, opened_by = :by, version = version + 1 where id = :id and status = 'PLANNED'",
                new MapSqlParameterSource("id", id).addValue("at", Timestamp.from(now)).addValue("by", actor.ownerId()));
        if (n == 0) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, "OPEN");
        }
        audit.record(new AuditService.Command(MODULE, "SESSION_OPEN", ENTITY, id, s.orgId(), s.branchId(), Map.of("title", s.title(), "date", s.date().toString())));
        return toResponse(load(scope, id));
    }

    @Transactional
    public AttendanceDtos.SessionResponse close(AuthenticatedActor actor, AccessScope scope, UUID id) {
        Row s = load(scope, id);
        if (!"OPEN".equals(s.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, s.status());
        }
        jdbc.update("update attendance_session set status = 'CLOSED', closed_at = :at, closed_by = :by, version = version + 1 where id = :id and status = 'OPEN'",
                new MapSqlParameterSource("id", id).addValue("at", Timestamp.from(clock.instant())).addValue("by", actor.ownerId()));
        AttendanceDtos.SessionResponse r = toResponse(load(scope, id));
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("attendees", r.attendees());
        d.put("anonymous", r.anonymousCount());
        audit.record(new AuditService.Command(MODULE, "SESSION_CLOSE", ENTITY, id, s.orgId(), s.branchId(), d));
        return r;
    }

    /** [V6] Reabrir exige la acción A, un motivo y estar dentro de reopenDays desde el cierre. */
    @Transactional
    public AttendanceDtos.SessionResponse reopen(AuthenticatedActor actor, AccessScope scope, UUID id, AttendanceDtos.ReopenRequest r) {
        Row s = load(scope, id);
        if (!"CLOSED".equals(s.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, s.status());
        }
        String reason = r == null || r.reason() == null ? "" : r.reason().trim();
        if (reason.length() < 5) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "motivo");
        }
        if (reason.length() > 255) {
            throw new Exceptions("error.common.tooLong", HttpStatus.BAD_REQUEST, "motivo", 255);
        }
        int days = rules.get(s.orgId()).reopenDays();
        Instant closed = s.closedAt() == null ? s.endsAt() : s.closedAt();
        if (clock.instant().isAfter(closed.plus(Duration.ofDays(days)))) {
            throw new Exceptions("error.attendance.reopenExpired", HttpStatus.UNPROCESSABLE_ENTITY, days);
        }
        jdbc.update("update attendance_session set status = 'OPEN', reopen_count = reopen_count + 1, last_reopen_at = :at, last_reopen_reason = :re, closed_at = null, closed_by = null,"
                        + " version = version + 1 where id = :id and status = 'CLOSED'",
                new MapSqlParameterSource("id", id).addValue("at", Timestamp.from(clock.instant())).addValue("re", reason));
        audit.record(new AuditService.Command(MODULE, "SESSION_REOPEN", ENTITY, id, s.orgId(), s.branchId(), Map.of("reason", reason)));
        return toResponse(load(scope, id));
    }

    /** [V7] Conteo de visitantes anónimos (entero ≥ 0), solo con la sesión abierta. */
    @Transactional
    public AttendanceDtos.SessionResponse setAnonymous(AccessScope scope, UUID id, AttendanceDtos.AnonymousRequest r) {
        Row s = load(scope, id);
        assertOpen(s);
        if (r == null || r.count() == null || r.count() < 0 || r.count() > 100_000) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "visitantes");
        }
        jdbc.update("update attendance_session set anonymous_count = :n, version = version + 1 where id = :id", new MapSqlParameterSource("id", id).addValue("n", r.count()));
        return toResponse(load(scope, id));
    }

    /** Solo se borra una sesión sin registros ni niños. */
    @Transactional
    public void delete(AccessScope scope, UUID id) {
        Row s = load(scope, id);
        Integer n = jdbc.queryForObject("select (select count(*) from attendance_record where session_id = :id) + (select count(*) from child_checkin where session_id = :id)",
                new MapSqlParameterSource("id", id), Integer.class);
        if ((n != null && n > 0) || s.anonymousCount() > 0) {
            throw new Exceptions("error.attendance.hasRecords", HttpStatus.CONFLICT);
        }
        jdbc.update("delete from attendance_session where id = :id", new MapSqlParameterSource("id", id));
        audit.record(new AuditService.Command(MODULE, "SESSION_DELETE", ENTITY, id, s.orgId(), s.branchId(), Map.of("title", s.title(), "date", s.date().toString())));
    }

    // ---------------------------------------------------------------- registros

    @Transactional(readOnly = true)
    public List<AttendanceDtos.RecordItem> records(AccessScope scope, UUID id) {
        Row s = load(scope, id);
        return jdbc.query("select r.person_id, trim(p.first_name || ' ' || p.last_name) as name, r.status, r.method, r.at from attendance_record r join person p on p.id = r.person_id"
                        + " where r.session_id = :id order by r.at desc limit " + MAX_RECORDS, new MapSqlParameterSource("id", s.id()),
                (rs, i) -> new AttendanceDtos.RecordItem((UUID) rs.getObject(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getTimestamp(5).toInstant()));
    }

    /** Lista para marcar: sin texto, las personas activas de la sede; con texto, cualquiera de la organización (por nombre o documento). */
    @Transactional(readOnly = true)
    public PageResponse<AttendanceDtos.RosterItem> roster(AccessScope scope, UUID id, AttendanceDtos.RosterSearch req) {
        Row s = load(scope, id);
        MapSqlParameterSource ps = new MapSqlParameterSource("sid", s.id()).addValue("o", s.orgId()).addValue("br", s.branchId());
        StringBuilder w = new StringBuilder("p.organization_id = :o and p.status = 'ACTIVE' and p.anonymized_at is null");
        String q = req == null || req.q() == null ? "" : req.q().trim().toLowerCase();
        if (q.isEmpty()) {
            w.append(" and p.primary_branch_id = :br");
        } else {
            String[] words = q.split("\\s+");
            for (int i = 0; i < words.length && i < 5; i++) {
                String k = "w" + i;
                String v = words[i].replace("%", "").replace("_", "");
                ps.addValue(k, "%" + v + "%").addValue(k + "d", v + "%");
                w.append(" and (lower(p.first_name || ' ' || p.last_name) like :").append(k).append(" or lower(coalesce(p.doc_number, '')) like :").append(k).append("d)");
            }
        }
        String from = " from person p left join branch b on b.id = p.primary_branch_id left join attendance_record r on r.session_id = :sid and r.person_id = p.id where " + w;
        Long total = jdbc.queryForObject("select count(*)" + from, ps, Long.class);
        int size = req == null || req.pagination() == null ? 40 : Math.max(1, Math.min(req.pagination().getSize(), 100));
        int page = req == null || req.pagination() == null ? 0 : Math.max(0, req.pagination().getPage());
        ps.addValue("lim", size).addValue("off", (long) page * size);
        List<AttendanceDtos.RosterItem> rows = jdbc.query("select p.id, trim(p.first_name || ' ' || p.last_name) as name, p.doc_number, b.name as bn, r.status, r.method" + from
                + " order by lower(p.last_name), lower(p.first_name) limit :lim offset :off", ps, (rs, i) -> {
            String doc = rs.getString(3);
            String masked = doc == null ? null : "•".repeat(Math.max(0, doc.length() - 3)) + doc.substring(Math.max(0, doc.length() - 3));
            return new AttendanceDtos.RosterItem((UUID) rs.getObject(1), rs.getString(2), masked, rs.getString(4), rs.getString(5), rs.getString(6));
        });
        long t = total == null ? 0 : total;
        return new PageResponse<>(rows, new PaginationResponse((int) t, (int) Math.ceil(t / (double) size), size, page));
    }

    @Transactional
    public AttendanceDtos.RecordResult record(AuthenticatedActor actor, AccessScope scope, UUID sessionId, AttendanceDtos.RecordRequest r) {
        Row s = load(scope, sessionId);
        assertOpen(s);
        if (r == null || r.personId() == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "persona");
        }
        String status = r.status() == null || r.status().isBlank() ? "PRESENT" : r.status().trim().toUpperCase();
        String method = r.method() == null || r.method().isBlank() ? "MANUAL" : r.method().trim().toUpperCase();
        if (!STATUSES.contains(status) || !Set.of("MANUAL", "LIST").contains(method)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "registro");
        }
        return recordInternal(s, r.personId(), status, method, actor.ownerId());
    }

    /** Registro por lote (cola sin conexión): cada persona se procesa por separado y repetir no duplica. */
    @Transactional
    public AttendanceDtos.BatchResult batch(AuthenticatedActor actor, AccessScope scope, UUID sessionId, AttendanceDtos.BatchRequest req) {
        Row s = load(scope, sessionId);
        assertOpen(s);
        if (req == null || req.items() == null || req.items().isEmpty() || req.items().size() > 500) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "lote");
        }
        int created = 0, updated = 0, unchanged = 0;
        List<AttendanceDtos.BatchFailure> failures = new ArrayList<>();
        for (AttendanceDtos.RecordRequest r : req.items()) {
            try {
                String status = r.status() == null || r.status().isBlank() ? "PRESENT" : r.status().trim().toUpperCase();
                String method = r.method() == null || r.method().isBlank() ? "MANUAL" : r.method().trim().toUpperCase();
                if (r.personId() == null || !STATUSES.contains(status) || !Set.of("MANUAL", "LIST").contains(method)) {
                    throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "registro");
                }
                AttendanceDtos.RecordResult x = recordInternal(s, r.personId(), status, method, actor.ownerId());
                if (x.created()) {
                    created++;
                } else if (x.changed()) {
                    updated++;
                } else {
                    unchanged++;
                }
            } catch (Exceptions e) {
                failures.add(new AttendanceDtos.BatchFailure(r.personId(), e.getMessage()));
            }
        }
        return new AttendanceDtos.BatchResult(created, updated, unchanged, failures.size(), failures);
    }

    @Transactional
    public void removeRecord(AccessScope scope, UUID sessionId, UUID personId) {
        Row s = load(scope, sessionId);
        assertOpen(s);
        int n = jdbc.update("delete from attendance_record where session_id = :s and person_id = :p", new MapSqlParameterSource("s", sessionId).addValue("p", personId));
        if (n == 0) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Registro idempotente [V5]: si ya existe con el mismo estado no cambia nada; si el estado es otro, lo actualiza.
     * Al registrar una asistencia (PRESENT/LATE) se cierra la alerta de inasistencia abierta de la persona.
     */
    AttendanceDtos.RecordResult recordInternal(Row s, UUID personId, String status, String method, UUID recordedBy) {
        if (!METHODS.contains(method) || !STATUSES.contains(status)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "registro");
        }
        List<Object[]> p = jdbc.query("select trim(first_name || ' ' || last_name), status, anonymized_at from person where id = :id and organization_id = :o",
                new MapSqlParameterSource("id", personId).addValue("o", s.orgId()), (rs, i) -> new Object[]{rs.getString(1), rs.getString(2), rs.getTimestamp(3)});
        if (p.isEmpty()) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        if (!"ACTIVE".equals(p.get(0)[1]) || p.get(0)[2] != null) {                                     // [V12]
            throw new Exceptions("error.attendance.personInactive", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        Instant now = clock.instant();
        MapSqlParameterSource ps = new MapSqlParameterSource("id", UUID.randomUUID()).addValue("s", s.id()).addValue("o", s.orgId()).addValue("b", s.branchId())
                .addValue("p", personId).addValue("st", status).addValue("m", method).addValue("at", Timestamp.from(now)).addValue("by", recordedBy);
        int ins = jdbc.update("insert into attendance_record (id, session_id, organization_id, branch_id, person_id, status, method, at, recorded_by)"
                + " values (:id, :s, :o, :b, :p, :st, :m, :at, :by) on conflict (session_id, person_id) do nothing", ps);
        boolean created = ins > 0;
        boolean changed = false;
        String finalStatus = status;
        String finalMethod = method;
        Instant finalAt = now;
        if (!created) {
            List<Object[]> cur = jdbc.query("select status, method, at from attendance_record where session_id = :s and person_id = :p", ps,
                    (rs, i) -> new Object[]{rs.getString(1), rs.getString(2), rs.getTimestamp(3).toInstant()});
            Object[] c = cur.get(0);
            finalStatus = (String) c[0];
            finalMethod = (String) c[1];
            finalAt = (Instant) c[2];
            if (!finalStatus.equals(status) && !"QR".equals(method) && !"SELF".equals(method)) {
                jdbc.update("update attendance_record set status = :st, recorded_by = :by where session_id = :s and person_id = :p", ps);
                finalStatus = status;
                changed = true;
            }
        }
        if (created || changed) {
            if ("PRESENT".equals(finalStatus) || "LATE".equals(finalStatus)) {
                jdbc.update("update attendance_absence_alert set status = 'RESOLVED', resolved_at = :at where person_id = :p and status = 'OPEN'", ps);
            }
        }
        return new AttendanceDtos.RecordResult(new AttendanceDtos.RecordItem(personId, (String) p.get(0)[0], finalStatus, finalMethod, finalAt), created, changed);
    }

    // ---------------------------------------------------------------- tendencias y exportación

    @Transactional(readOnly = true)
    public AttendanceDtos.Trends trends(AccessScope scope, AttendanceDtos.TrendsRequest req) {
        LocalDate to = req == null || req.to() == null ? LocalDate.now(clock) : req.to();
        LocalDate from = req == null || req.from() == null ? to.minusDays(90) : req.from();
        if (from.isAfter(to)) {
            throw new Exceptions("error.common.dateRange", HttpStatus.BAD_REQUEST);
        }
        MapSqlParameterSource ps = new MapSqlParameterSource("df", java.sql.Date.valueOf(from)).addValue("dt", java.sql.Date.valueOf(to));
        StringBuilder w = new StringBuilder(visible(scope, ps)).append(" and s.status = 'CLOSED' and s.session_date between :df and :dt");
        if (req != null && req.branchId() != null) {
            w.append(" and s.branch_id = :fb");
            ps.addValue("fb", req.branchId());
        }
        if (req != null && req.serviceId() != null) {
            w.append(" and s.context_type = 'SERVICE' and s.context_id = :fs");
            ps.addValue("fs", req.serviceId());
        }
        List<AttendanceDtos.TrendPoint> points = jdbc.query("select s.id, s.session_date, s.title, s.branch_id, b.name as bn,"
                + " count(r.id) filter (where r.status in ('PRESENT','LATE')) as att, count(r.id) filter (where r.status = 'LATE') as late,"
                + " count(r.id) filter (where r.status = 'EXCUSED') as exc, s.anonymous_count"
                + " from attendance_session s join branch b on b.id = s.branch_id left join attendance_record r on r.session_id = s.id where " + w
                + " group by s.id, b.name order by s.session_date, s.starts_at limit 1000", ps, (rs, i) -> {
            int att = rs.getInt("att");
            int anon = rs.getInt("anonymous_count");
            return new AttendanceDtos.TrendPoint((UUID) rs.getObject("id"), rs.getDate("session_date").toLocalDate(), rs.getString("title"), (UUID) rs.getObject("branch_id"),
                    rs.getString("bn"), att, rs.getInt("late"), rs.getInt("exc"), anon, att + anon);
        });
        Map<UUID, int[]> agg = new LinkedHashMap<>();
        Map<UUID, String> names = new LinkedHashMap<>();
        int total = 0;
        for (AttendanceDtos.TrendPoint p : points) {
            int[] a = agg.computeIfAbsent(p.branchId(), k -> new int[2]);
            a[0]++;
            a[1] += p.total();
            names.put(p.branchId(), p.branchName());
            total += p.total();
        }
        List<AttendanceDtos.TrendBranch> byBranch = new ArrayList<>();
        agg.forEach((k, a) -> byBranch.add(new AttendanceDtos.TrendBranch(k, names.get(k), a[0], a[1], a[0] == 0 ? 0 : Math.round(a[1] * 10.0 / a[0]) / 10.0)));
        double avg = points.isEmpty() ? 0 : Math.round(total * 10.0 / points.size()) / 10.0;
        return new AttendanceDtos.Trends(points, byBranch, points.size(), total, avg);
    }

    public record ExportResult(byte[] content, int rows) {
    }

    @Transactional
    public ExportResult exportSession(AuthenticatedActor actor, AccessScope scope, UUID id) {
        Row s = load(scope, id);
        List<AttendanceDtos.RecordItem> items = records(scope, id);
        List<List<Object>> data = new ArrayList<>();
        for (AttendanceDtos.RecordItem i : items) {
            data.add(java.util.Arrays.asList(i.fullName(), i.status(), i.method(), i.at().toString()));
        }
        data.add(java.util.Arrays.asList("Visitantes sin registro", "PRESENT", "COUNT", String.valueOf(s.anonymousCount())));
        byte[] bytes = XlsxWriter.write("Asistencia", List.of("Persona", "Estado", "Método", "Hora"), data);
        audit.record(new AuditService.Command(MODULE, "EXPORT", ENTITY, id, s.orgId(), s.branchId(), Map.of("rows", items.size())));
        return new ExportResult(bytes, items.size());
    }

    // ---------------------------------------------------------------- internos

    /** Alcance de sedes; quien es ORG_USER solo ve la sesión abierta. */
    static String visible(AccessScope scope, MapSqlParameterSource ps) {
        String w = AttendanceSupport.branchScope(scope, ps, "s");
        return scope.role() == RoleType.ORG_USER ? w + " and s.status = 'OPEN'" : w;
    }

    Row load(AccessScope scope, UUID id) {
        MapSqlParameterSource ps = new MapSqlParameterSource("id", id);
        String w = visible(scope, ps);
        return jdbc.query("select s.* from attendance_session s where s.id = :id and " + w, ps, (rs, i) -> row(rs)).stream().findFirst()
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    /** Sesión sin restricción de alcance (jobs y QR). */
    Row loadRaw(UUID id) {
        return jdbc.query("select s.* from attendance_session s where s.id = :id", new MapSqlParameterSource("id", id), (rs, i) -> row(rs)).stream().findFirst()
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    void assertOpen(Row s) {
        if ("CLOSED".equals(s.status())) {
            throw new Exceptions("error.attendance.sessionClosed", HttpStatus.CONFLICT);
        }
        if (!"OPEN".equals(s.status())) {
            throw new Exceptions("error.attendance.sessionNotOpen", HttpStatus.CONFLICT);
        }
    }

    private static Row row(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp oa = rs.getTimestamp("opened_at");
        Timestamp ca = rs.getTimestamp("closed_at");
        return new Row((UUID) rs.getObject("id"), (UUID) rs.getObject("organization_id"), (UUID) rs.getObject("branch_id"), rs.getString("context_type"),
                (UUID) rs.getObject("context_id"), rs.getString("title"), rs.getDate("session_date").toLocalDate(), rs.getTimestamp("starts_at").toInstant(),
                rs.getTimestamp("ends_at").toInstant(), rs.getString("status"), rs.getInt("anonymous_count"), rs.getBoolean("self_checkin"),
                oa == null ? null : oa.toInstant(), ca == null ? null : ca.toInstant(), rs.getInt("reopen_count"), rs.getString("last_reopen_reason"), rs.getLong("version"));
    }

    private AttendanceDtos.SessionResponse toResponse(Row s) {
        MapSqlParameterSource ps = new MapSqlParameterSource("id", s.id());
        int att = jdbc.queryForObject("select count(*) from attendance_record where session_id = :id and status in ('PRESENT','LATE')", ps, Integer.class);
        int exc = jdbc.queryForObject("select count(*) from attendance_record where session_id = :id and status = 'EXCUSED'", ps, Integer.class);
        Instant reopenUntil = "CLOSED".equals(s.status()) && s.closedAt() != null ? s.closedAt().plus(Duration.ofDays(rules.get(s.orgId()).reopenDays())) : null;
        return new AttendanceDtos.SessionResponse(s.id(), s.branchId(), support.branchName(s.branchId()), s.contextType(), s.contextId(), s.title(), s.date(), s.startsAt(), s.endsAt(),
                s.status(), att, exc, s.anonymousCount(), att + s.anonymousCount(), s.selfCheckin(), s.startsAt().minus(OPEN_BEFORE), s.openedAt(), s.closedAt(), s.reopenCount(),
                s.lastReopenReason(), reopenUntil, s.version());
    }
}
