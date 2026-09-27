package pe.dcs.app.features.facility.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.approval.service.ApprovalEngine;
import pe.dcs.app.features.approval.service.ApprovalHandler;
import pe.dcs.app.features.facility.dto.FacilityDtos;
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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * M16 · Reservas (SPACES). [V5] sin solape con CONFIRMED (+buffer) → 409, comprobado bajo bloqueo de fila del espacio para que
 * dos solicitudes simultáneas al mismo hueco no puedan confirmarse ambas [M16-T02]. Si el espacio exige aprobación, la reserva
 * nace PENDING y abre una solicitud en el motor genérico (M21, tipo SPACE_RESERVATION); si no, nace CONFIRMED de una vez.
 * Recurrencia: sin confirmar, {@link #submit} solo informa los choques (vista previa, no persiste nada); confirmando, crea las
 * ocurrencias libres y omite las que chocan [M16-T04]. [D1, ver V27] las reservas automáticas de M14/M10/M13 (createLinked) están
 * listas pero ningún módulo las llama todavía — integración pendiente de una entrega aparte.
 */
@Service
@RequiredArgsConstructor
public class ReservationService {

    public static final String APPROVAL_TYPE = "SPACE_RESERVATION";
    private static final String ENTITY = "Reservation";

    private final NamedParameterJdbcTemplate jdbc;
    private final AuthorizationService authz;
    private final FacilitySupport support;
    private final SpaceService spaces;
    private final ApprovalEngine engine;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Clock clock;

    record Row(UUID id, UUID orgId, UUID spaceId, UUID branchId, UUID requestedBy, String title, String status, Instant startAt, Instant endAt,
              UUID approvalRequestId) {
    }

    // ---------------------------------------------------------------- consulta

    @Transactional(readOnly = true)
    public PageResponse<FacilityDtos.ReservationView> search(AccessScope scope, FacilityDtos.ReservationSearch req) {
        FacilityDtos.ReservationSearch.ReservationFilters f = req == null || req.filters() == null
                ? new FacilityDtos.ReservationSearch.ReservationFilters(null, null, null, null, null, null) : req.filters();
        MapSqlParameterSource ps = new MapSqlParameterSource("org", scope.organizationId());
        StringBuilder w = new StringBuilder("r.organization_id = :org");
        if (!scope.allBranches()) {
            ps.addValue("sb", FacilitySupport.branchIdsOrNone(scope));
            w.append(" and sp.branch_id in (:sb)");
        }
        if (f.spaceId() != null) {
            w.append(" and r.space_id = :fs");
            ps.addValue("fs", f.spaceId());
        }
        if (f.branchId() != null) {
            w.append(" and sp.branch_id = :fb");
            ps.addValue("fb", f.branchId());
        }
        if (FacilitySupport.hasText(f.status())) {
            w.append(" and r.status = :st");
            ps.addValue("st", f.status().trim().toUpperCase());
        }
        if (f.from() != null) {
            w.append(" and r.end_at >= :df");
            ps.addValue("df", Timestamp.from(f.from()));
        }
        if (f.to() != null) {
            w.append(" and r.start_at <= :dt");
            ps.addValue("dt", Timestamp.from(f.to()));
        }
        if (Boolean.TRUE.equals(f.mine())) {
            w.append(" and r.requested_by = :me");
            ps.addValue("me", scope.personId());
        }
        String from = " from reservation r join space sp on sp.id = r.space_id left join person p on p.id = r.requested_by where ";
        Long total = jdbc.queryForObject("select count(*)" + from + w, ps, Long.class);
        int size = req == null || req.pagination() == null ? 20 : Math.max(1, Math.min(req.pagination().getSize(), 200));
        int page = req == null || req.pagination() == null ? 0 : Math.max(0, req.pagination().getPage());
        ps.addValue("lim", size).addValue("off", (long) page * size);
        List<FacilityDtos.ReservationView> rows = jdbc.query(SELECT + from + w + " order by r.start_at desc limit :lim offset :off", ps, (rs, i) -> view(rs));
        long t = total == null ? 0 : total;
        return new PageResponse<>(rows, new PaginationResponse((int) t, (int) Math.ceil(t / (double) size), size, page));
    }

    private static final String SELECT = "select r.*, sp.name as space_name, sp.branch_id as sp_branch_id, sp.capacity as sp_capacity,"
            + " b.name as branch_name, trim(p.first_name || ' ' || p.last_name) as requested_by_name";

    @Transactional(readOnly = true)
    public FacilityDtos.ReservationView get(AccessScope scope, UUID id) {
        MapSqlParameterSource ps = new MapSqlParameterSource("id", id).addValue("org", scope.organizationId());
        String vis = scope.allBranches() ? "" : " and sp.branch_id in (:sb)";
        if (!scope.allBranches()) {
            ps.addValue("sb", FacilitySupport.branchIdsOrNone(scope));
        }
        return jdbc.query(SELECT + " from reservation r join space sp on sp.id = r.space_id left join branch b on b.id = sp.branch_id"
                        + " left join person p on p.id = r.requested_by where r.id = :id and r.organization_id = :org" + vis, ps, (rs, i) -> view(rs))
                .stream().findFirst().orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    private Row raw(UUID id) {
        List<Row> r = jdbc.query("select id, organization_id, space_id, requested_by, title, status, start_at, end_at, approval_request_id,"
                        + " (select branch_id from space where id = reservation.space_id) as branch_id from reservation where id = :id",
                new MapSqlParameterSource("id", id), (rs, i) -> new Row((UUID) rs.getObject("id"), (UUID) rs.getObject("organization_id"),
                        (UUID) rs.getObject("space_id"), (UUID) rs.getObject("branch_id"), (UUID) rs.getObject("requested_by"), rs.getString("title"),
                        rs.getString("status"), rs.getTimestamp("start_at").toInstant(), rs.getTimestamp("end_at").toInstant(),
                        (UUID) rs.getObject("approval_request_id")));
        if (r.isEmpty()) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        return r.get(0);
    }

    // ---------------------------------------------------------------- alta (individual o recurrente)

    @Transactional
    public FacilityDtos.ReservationSubmitResult submit(AuthenticatedActor actor, AccessScope scope, FacilityDtos.ReservationRequest r) {
        authz.require(actor, FacilitySupport.MOD_SPACES, Action.C);
        if (r == null || r.spaceId() == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "espacio");
        }
        SpaceService.Row space = spaces.raw(r.spaceId());
        if (!scope.canSeeBranch(space.branchId())) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        if (!"ACTIVE".equals(space.status())) {
            throw new Exceptions("error.space.notBookable", HttpStatus.UNPROCESSABLE_ENTITY);                               // [V6]
        }
        String title = FacilitySupport.trim(r.title(), 150, "título");
        if (title == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "título");
        }
        if (r.startAt() == null || r.endAt() == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "horario");
        }
        if (!r.endAt().isAfter(r.startAt())) {
            throw new Exceptions("error.reservation.timeInvalid", HttpStatus.BAD_REQUEST);                                  // [V4]
        }
        if (r.startAt().isBefore(clock.instant())) {
            throw new Exceptions("error.reservation.pastStart", HttpStatus.BAD_REQUEST);                                    // [V4]
        }
        SpaceService.Row2 rules = spaces.rulesRaw(scope.organizationId());
        Duration exact = Duration.between(r.startAt(), r.endAt());
        if (exact.toHours() > rules.maxDurationHours() || (exact.toHours() == rules.maxDurationHours() && exact.toMinutesPart() > 0)) {
            throw new Exceptions("error.reservation.tooLong", HttpStatus.UNPROCESSABLE_ENTITY, rules.maxDurationHours());   // [V4]
        }
        String sourceType = r.sourceType() == null || r.sourceType().isBlank() ? "OTHER" : r.sourceType().trim().toUpperCase();
        if (!Set.of("EVENT", "SMALL_GROUP", "BIBLE_CLASS", "OTHER").contains(sourceType)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "origen");
        }

        int occurrences = r.recurrence() == null || r.recurrence().occurrences() == null ? 1 : r.recurrence().occurrences();
        if (occurrences < 1 || occurrences > rules.maxRecurrence()) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "recurrencia");
        }
        Duration span = Duration.between(r.startAt(), r.endAt());
        List<Instant[]> plan = new ArrayList<>();
        for (int i = 0; i < occurrences; i++) {
            Instant s = r.startAt().plus(Duration.ofDays(7L * i));
            plan.add(new Instant[]{s, s.plus(span)});
        }
        boolean recurring = occurrences > 1;
        boolean confirmStep = !recurring || Boolean.TRUE.equals(r.confirm());

        if (recurring && !confirmStep) {
            // vista previa: no persiste nada, solo informa choques [M16-T04]
            List<FacilityDtos.OccurrenceConflict> conflicts = new ArrayList<>();
            for (Instant[] occ : plan) {
                if (overlaps(r.spaceId(), occ[0], occ[1], space.bufferMinutes())) {
                    conflicts.add(new FacilityDtos.OccurrenceConflict(occ[0], occ[1]));
                }
            }
            return new FacilityDtos.ReservationSubmitResult(true, List.of(), conflicts, plan.size());
        }

        // alta real: bloquea la fila del espacio para serializar con otras solicitudes concurrentes al mismo hueco [M16-T02]
        jdbc.queryForList("select id from space where id = :id for update", new MapSqlParameterSource("id", r.spaceId()), UUID.class);
        UUID recurrenceGroup = recurring ? UUID.randomUUID() : null;
        List<FacilityDtos.ReservationView> created = new ArrayList<>();
        List<FacilityDtos.OccurrenceConflict> conflicts = new ArrayList<>();
        for (Instant[] occ : plan) {
            boolean overlap = overlaps(r.spaceId(), occ[0], occ[1], space.bufferMinutes());
            if (overlap) {
                if (!recurring) {
                    throw new Exceptions("error.reservation.overlap", HttpStatus.CONFLICT);                                 // [V5]
                }
                conflicts.add(new FacilityDtos.OccurrenceConflict(occ[0], occ[1]));
                continue;
            }
            UUID id = UUID.randomUUID();
            boolean requiresApproval = space.requiresApproval();
            String status = requiresApproval ? "PENDING" : "CONFIRMED";
            jdbc.update("insert into reservation (id, organization_id, space_id, requested_by, title, source_type, source_id, start_at, end_at,"
                            + " recurrence_group_id, attendees_est, status, created_at, created_by) values (:id, :o, :sp, :rb, :t, :src, :sid, :s, :e, :rg,"
                            + " :att, :st, :at, :by)",
                    new MapSqlParameterSource("id", id).addValue("o", scope.organizationId()).addValue("sp", r.spaceId()).addValue("rb", scope.personId())
                            .addValue("t", title).addValue("src", sourceType).addValue("sid", r.sourceId()).addValue("s", Timestamp.from(occ[0]))
                            .addValue("e", Timestamp.from(occ[1])).addValue("rg", recurrenceGroup).addValue("att", r.attendeesEst())
                            .addValue("st", status).addValue("at", Timestamp.from(clock.instant())).addValue("by", scope.personId()));
            if (requiresApproval) {
                Map<String, Object> payload = new java.util.LinkedHashMap<>();
                payload.put("spaceName", spaceNameOf(r.spaceId()));
                payload.put("branchName", support.branchName(space.branchId()));
                payload.put("title", title);
                UUID reqId = engine.open(new ApprovalEngine.NewRequest(scope.organizationId(), space.branchId(), null, APPROVAL_TYPE, "RESERVATION", id,
                        scope.personId(), null, payload));
                jdbc.update("update reservation set approval_request_id = :r where id = :id", new MapSqlParameterSource("r", reqId).addValue("id", id));
            } else {
                notifications.toPersons(NotificationType.SPACE_RESERVATION_APPROVED, scope.organizationId(), List.of(scope.personId()),
                        Map.of("space", spaceNameOf(r.spaceId()), "title", title), "/app/spaces/reservations/" + id, null);
            }
            audit.record(new AuditService.Command(FacilitySupport.MOD_SPACES, "RESERVE", ENTITY, id, scope.organizationId(), space.branchId(),
                    Map.of("space", r.spaceId().toString(), "status", status)));
            created.add(get(scope, id));
        }
        return new FacilityDtos.ReservationSubmitResult(false, created, conflicts, plan.size());
    }

    /** Reserva ya CONFIRMED y enlazada a otro módulo (eventos/grupos/dictados) — lista para usarse, sin llamador todavía [D1]. */
    @Transactional
    public UUID createLinked(UUID orgId, UUID spaceId, UUID requestedBy, String title, String sourceType, UUID sourceId, Instant startAt, Instant endAt) {
        SpaceService.Row space = spaces.raw(spaceId);
        jdbc.queryForList("select id from space where id = :id for update", new MapSqlParameterSource("id", spaceId), UUID.class);
        if (overlaps(spaceId, startAt, endAt, space.bufferMinutes())) {
            throw new Exceptions("error.reservation.overlap", HttpStatus.CONFLICT);
        }
        UUID id = UUID.randomUUID();
        jdbc.update("insert into reservation (id, organization_id, space_id, requested_by, title, source_type, source_id, start_at, end_at, status,"
                        + " created_at, created_by) values (:id, :o, :sp, :rb, :t, :src, :sid, :s, :e, 'CONFIRMED', :at, :by)",
                new MapSqlParameterSource("id", id).addValue("o", orgId).addValue("sp", spaceId).addValue("rb", requestedBy)
                        .addValue("t", FacilitySupport.trim(title, 150, "título")).addValue("src", sourceType).addValue("sid", sourceId)
                        .addValue("s", Timestamp.from(startAt)).addValue("e", Timestamp.from(endAt)).addValue("at", Timestamp.from(clock.instant()))
                        .addValue("by", requestedBy));
        audit.record(new AuditService.Command(FacilitySupport.MOD_SPACES, "RESERVE_LINKED", ENTITY, id, orgId, space.branchId(),
                Map.of("source", sourceType + ":" + sourceId)));
        return id;
    }

    /**
     * M13→M16 · Genera una serie semanal de reservas CONFIRMED enlazadas a un dictado (una fila por sesión, entre {@code startDate}
     * y {@code endDate} en el día de la semana indicado), con el mismo criterio de "generar ocurrencias y saltar las que chocan"
     * que {@link #submit} ya usa para la reserva manual recurrente — a diferencia de {@link #createLinked}, aquí un choque en una
     * sola fecha NO lanza excepción ni bloquea el resto de la serie: esa fecha simplemente queda sin espacio reservado (no tiene
     * sentido impedir un curso de varios meses por un solo día ocupado). Devuelve los ids creados; el llamador puede comparar el
     * tamaño contra las fechas esperadas si quiere avisar de huecos.
     */
    private static final int MAX_SERIES_OCCURRENCES = 80;

    @Transactional
    public List<UUID> createLinkedSeries(UUID orgId, UUID spaceId, UUID requestedBy, String title, String sourceType, UUID sourceId,
            Integer dayOfWeek, LocalDate startDate, LocalDate endDate, LocalTime startTime, LocalTime endTime, java.time.ZoneId zone) {
        SpaceService.Row space = spaces.raw(spaceId);
        jdbc.queryForList("select id from space where id = :id for update", new MapSqlParameterSource("id", spaceId), UUID.class);
        List<UUID> created = new ArrayList<>();
        // sin patrón semanal fijo (taller de una sola fecha): una única ocurrencia en startDate, mismo criterio que
        // CourseClassService.classDates() usa para calcular las fechas de asistencia de un dictado sin día de la semana.
        LocalDate d = dayOfWeek == null ? startDate : startDate.with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.of(dayOfWeek)));
        int guard = 0;
        while (!d.isAfter(endDate) && guard++ < MAX_SERIES_OCCURRENCES) {
            Instant s = d.atTime(startTime).atZone(zone).toInstant();
            Instant e = d.atTime(endTime).atZone(zone).toInstant();
            if (!overlaps(spaceId, s, e, space.bufferMinutes())) {
                UUID id = UUID.randomUUID();
                jdbc.update("insert into reservation (id, organization_id, space_id, requested_by, title, source_type, source_id, start_at, end_at, status,"
                                + " created_at, created_by) values (:id, :o, :sp, :rb, :t, :src, :sid, :s, :e, 'CONFIRMED', :at, :by)",
                        new MapSqlParameterSource("id", id).addValue("o", orgId).addValue("sp", spaceId).addValue("rb", requestedBy)
                                .addValue("t", FacilitySupport.trim(title, 150, "título")).addValue("src", sourceType).addValue("sid", sourceId)
                                .addValue("s", Timestamp.from(s)).addValue("e", Timestamp.from(e)).addValue("at", Timestamp.from(clock.instant()))
                                .addValue("by", requestedBy));
                created.add(id);
            }
            d = dayOfWeek == null ? endDate.plusDays(1) : d.plusWeeks(1);
        }
        if (!created.isEmpty()) {
            audit.record(new AuditService.Command(FacilitySupport.MOD_SPACES, "RESERVE_LINKED_SERIES", ENTITY, sourceId, orgId, space.branchId(),
                    Map.of("source", sourceType + ":" + sourceId, "count", created.size())));
        }
        return created;
    }

    /** Cancela las reservas enlazadas a un origen (evento/grupo/dictado) cancelado — lista para usarse, sin llamador todavía [D1]. */
    @Transactional
    public void cancelBySource(UUID orgId, String sourceType, UUID sourceId, String reason) {
        List<UUID> ids = jdbc.queryForList("select id from reservation where organization_id = :o and source_type = :st and source_id = :sid and status in ('PENDING','CONFIRMED')",
                new MapSqlParameterSource("o", orgId).addValue("st", sourceType).addValue("sid", sourceId), UUID.class);
        for (UUID id : ids) {
            jdbc.update("update reservation set status = 'CANCELLED', decision_reason = :r, updated_at = :at, version = version + 1 where id = :id",
                    new MapSqlParameterSource("r", reason).addValue("at", Timestamp.from(clock.instant())).addValue("id", id));
        }
    }

    // ---------------------------------------------------------------- decisión (aprobación)

    @Transactional
    public void approve(AuthenticatedActor actor, AccessScope scope, UUID id, String note) {
        Row r = raw(id);
        if (r.approvalRequestId() == null) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, r.status());
        }
        engine.approve(actor, scope, r.approvalRequestId(), note);
    }

    @Transactional
    public void reject(AuthenticatedActor actor, AccessScope scope, UUID id, String reason) {
        Row r = raw(id);
        if (r.approvalRequestId() == null) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, r.status());
        }
        if (FacilitySupport.trim(reason, 500, "motivo") == null) {
            throw new Exceptions("error.reservation.rejectReason", HttpStatus.BAD_REQUEST);                                 // [V8]
        }
        engine.reject(actor, scope, r.approvalRequestId(), reason);
    }

    void onApproved(ApprovalHandler.ApprovalRow req) {
        Row r = raw(req.subjectId());
        SpaceService.Row space = spaces.raw(r.spaceId());
        jdbc.queryForList("select id from space where id = :id for update", new MapSqlParameterSource("id", r.spaceId()), UUID.class);
        if (overlaps(r.spaceId(), r.startAt(), r.endAt(), space.bufferMinutes(), r.id())) {
            throw new Exceptions("error.reservation.overlap", HttpStatus.CONFLICT);                                         // recomprobado al aprobar
        }
        jdbc.update("update reservation set status = 'CONFIRMED', updated_at = :at, version = version + 1 where id = :id and status = 'PENDING'",
                new MapSqlParameterSource("at", Timestamp.from(clock.instant())).addValue("id", r.id()));
    }

    void onRejected(ApprovalHandler.ApprovalRow req, String note) {
        jdbc.update("update reservation set status = 'REJECTED', decision_reason = :r, updated_at = :at, version = version + 1 where id = :id",
                new MapSqlParameterSource("r", note).addValue("at", Timestamp.from(clock.instant())).addValue("id", req.subjectId()));
    }

    void onCancelled(ApprovalHandler.ApprovalRow req) {
        jdbc.update("update reservation set status = 'CANCELLED', updated_at = :at, version = version + 1 where id = :id",
                new MapSqlParameterSource("at", Timestamp.from(clock.instant())).addValue("id", req.subjectId()));
    }

    Map<String, String> notifyParams(ApprovalHandler.ApprovalRow r) {
        Map<String, String> m = new java.util.HashMap<>();
        m.put("space", String.valueOf(r.payload().get("spaceName")));
        m.put("branch", String.valueOf(r.payload().get("branchName")));
        m.put("title", String.valueOf(r.payload().get("title")));
        return m;
    }

    // ---------------------------------------------------------------- cancelar (administración; autoservicio queda para M24)

    @Transactional
    public void cancel(AuthenticatedActor actor, AccessScope scope, UUID id, String reason) {
        authz.require(actor, FacilitySupport.MOD_SPACES, Action.S);
        FacilityDtos.ReservationView cur = get(scope, id);
        if (!Set.of("PENDING", "CONFIRMED").contains(cur.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, cur.status());
        }
        if ("PENDING".equals(cur.status())) {
            Row r = raw(id);
            if (r.approvalRequestId() != null) {
                engine.decideInternal(r.approvalRequestId(), false, scope.personId(), reason == null ? "Cancelada" : reason);
                jdbc.update("update reservation set status = 'CANCELLED', decision_reason = :r, updated_at = :at, version = version + 1 where id = :id",
                        new MapSqlParameterSource("r", reason).addValue("at", Timestamp.from(clock.instant())).addValue("id", id));
                return;
            }
        }
        jdbc.update("update reservation set status = 'CANCELLED', decision_reason = :r, updated_at = :at, version = version + 1 where id = :id",
                new MapSqlParameterSource("r", reason).addValue("at", Timestamp.from(clock.instant())).addValue("id", id));
        audit.record(new AuditService.Command(FacilitySupport.MOD_SPACES, "CANCEL", ENTITY, id, scope.organizationId(), null, Map.of()));
    }

    // ---------------------------------------------------------------- disponibilidad [V18] no revela quién reservó

    @Transactional(readOnly = true)
    public FacilityDtos.AvailabilityView availability(AccessScope scope, UUID spaceId, LocalDate date) {
        FacilityDtos.SpaceView space = spaces.get(scope, spaceId);
        Instant from = date.atStartOfDay(support.zoneOf(space.branchId(), scope.organizationId())).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(support.zoneOf(space.branchId(), scope.organizationId())).toInstant();
        List<FacilityDtos.BusyWindow> busy = jdbc.query("select start_at, end_at from reservation where space_id = :id and status = 'CONFIRMED'"
                        + " and start_at < :to and end_at > :from order by start_at",
                new MapSqlParameterSource("id", spaceId).addValue("from", Timestamp.from(from)).addValue("to", Timestamp.from(to)),
                (rs, i) -> new FacilityDtos.BusyWindow(rs.getTimestamp(1).toInstant(), rs.getTimestamp(2).toInstant()));
        return new FacilityDtos.AvailabilityView(spaceId, date, space.openFrom(), space.openTo(), busy);
    }

    // ---------------------------------------------------------------- helpers

    private boolean overlaps(UUID spaceId, Instant start, Instant end, int bufferMinutes) {
        return overlaps(spaceId, start, end, bufferMinutes, null);
    }

    /** [V5] Compara contra CONFIRMED existentes, expandidas por el buffer del espacio; excludeId evita comparar consigo misma al reconfirmar. */
    private boolean overlaps(UUID spaceId, Instant start, Instant end, int bufferMinutes, UUID excludeId) {
        MapSqlParameterSource ps = new MapSqlParameterSource("sp", spaceId).addValue("s", Timestamp.from(start)).addValue("e", Timestamp.from(end))
                .addValue("buf", bufferMinutes);
        String excl = excludeId == null ? "" : " and id <> :ex";
        if (excludeId != null) {
            ps.addValue("ex", excludeId);
        }
        Integer n = jdbc.queryForObject("select count(*) from reservation where space_id = :sp and status = 'CONFIRMED'" + excl
                        + " and (start_at - (:buf || ' minutes')::interval) < :e and (end_at + (:buf || ' minutes')::interval) > :s",
                ps, Integer.class);
        return n != null && n > 0;
    }

    private String spaceNameOf(UUID spaceId) {
        List<String> n = jdbc.queryForList("select name from space where id = :id", new MapSqlParameterSource("id", spaceId), String.class);
        return n.isEmpty() ? "" : n.get(0);
    }

    private static FacilityDtos.ReservationView view(java.sql.ResultSet rs) throws java.sql.SQLException {
        Integer capacity = (Integer) rs.getObject("sp_capacity");
        Integer attendees = (Integer) rs.getObject("attendees_est");
        boolean overCapacity = capacity != null && attendees != null && attendees > capacity;                              // [V7]
        return new FacilityDtos.ReservationView((UUID) rs.getObject("id"), (UUID) rs.getObject("space_id"), rs.getString("space_name"),
                (UUID) rs.getObject("sp_branch_id"), rs.getString("branch_name"), (UUID) rs.getObject("requested_by"), rs.getString("requested_by_name"),
                rs.getString("title"), rs.getString("source_type"), (UUID) rs.getObject("source_id"), rs.getTimestamp("start_at").toInstant(),
                rs.getTimestamp("end_at").toInstant(), attendees, rs.getString("status"), rs.getString("decision_reason"),
                (UUID) rs.getObject("recurrence_group_id"), overCapacity, rs.getTimestamp("created_at").toInstant(), rs.getLong("version"));
    }
}
