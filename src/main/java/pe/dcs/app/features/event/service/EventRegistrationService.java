package pe.dcs.app.features.event.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.attendance.dto.AttendanceDtos;
import pe.dcs.app.features.attendance.service.AttendanceService;
import pe.dcs.app.features.event.dto.EventDtos;
import pe.dcs.app.features.finance.service.FinanceSupport;
import pe.dcs.app.features.finance.service.FinancialMovementService;
import pe.dcs.app.features.notification.domain.NotificationType;
import pe.dcs.app.features.notification.service.NotificationService;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.AuthorizationService;
import pe.dcs.app.security.authz.ContractGate;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PaginationResponse;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M14 · Inscripciones: REGISTERED | WAITLISTED (FIFO al liberarse cupo) → CANCELLED. Pago PENDING → PAID (confirma otra
 * persona distinta de quien lo registró [T-C6], provisional hasta que M15 tenga su propio flujo de aprobación de movimientos)
 * → REFUNDED/WAIVED. Ticket QR de un solo uso [V14]; check-in reusa AttendanceService (núcleo M09, contexto EVENT).
 */
@Service
@RequiredArgsConstructor
public class EventRegistrationService {

    private static final String MODULE = EventSupport.MODULE;
    private static final String ENTITY = "EventRegistration";
    private static final String CONTEXT = "EVENT";
    private static final TypeReference<Map<String, String>> ANSWERS = new TypeReference<>() { };

    private final NamedParameterJdbcTemplate jdbc;
    private final EventSupport support;
    private final EventService events;
    private final AttendanceService attendance;
    private final NotificationService notifications;
    private final AuthorizationService authz;
    private final AuditService audit;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final ContractGate contractGate;
    private final org.springframework.beans.factory.ObjectProvider<FinancialMovementService> financeProvider;

    record RRow(UUID id, UUID orgId, UUID branchId, UUID eventId, UUID personId, String category, String status, String paymentStatus, BigDecimal amount,
               String currency, UUID submittedBy, UUID confirmedBy, Instant createdAt, long version) {
    }

    // ---------------------------------------------------------------- consulta

    @Transactional(readOnly = true)
    public PageResponse<EventDtos.RegistrationSummary> search(AccessScope scope, EventDtos.RegistrationSearch req) {
        EventDtos.RegistrationSearch.RegistrationFilters f = req == null || req.filters() == null
                ? new EventDtos.RegistrationSearch.RegistrationFilters(null, null, null, null) : req.filters();
        MapSqlParameterSource ps = new MapSqlParameterSource();
        StringBuilder w = new StringBuilder(EventSupport.visibleEvent(scope, ps, "e"));
        if (f.eventId() != null) {
            w.append(" and r.event_id = :fe");
            ps.addValue("fe", f.eventId());
        }
        if (f.personId() != null) {
            w.append(" and r.person_id = :fp");
            ps.addValue("fp", f.personId());
        }
        if (EventSupport.hasText(f.status())) {
            w.append(" and r.status = :fs");
            ps.addValue("fs", f.status().trim().toUpperCase());
        }
        if (EventSupport.hasText(f.paymentStatus())) {
            w.append(" and r.payment_status = :fps");
            ps.addValue("fps", f.paymentStatus().trim().toUpperCase());
        }
        String from = " from event_registration r join org_event e on e.id = r.event_id join person p on p.id = r.person_id where ";
        Long total = jdbc.queryForObject("select count(*)" + from + w, ps, Long.class);
        int size = req == null || req.pagination() == null ? 20 : Math.max(1, Math.min(req.pagination().getSize(), 200));
        int page = req == null || req.pagination() == null ? 0 : Math.max(0, req.pagination().getPage());
        ps.addValue("lim", size).addValue("off", (long) page * size);
        List<EventDtos.RegistrationSummary> rows = jdbc.query(SUMMARY + from + w + " order by r.created_at desc limit :lim offset :off", ps, (rs, i) -> summary(rs));
        long t = total == null ? 0 : total;
        return new PageResponse<>(rows, new PaginationResponse((int) t, (int) Math.ceil(t / (double) size), size, page));
    }

    private static final String SUMMARY = "select r.id, r.event_id, e.name as event_name, r.person_id, trim(p.first_name || ' ' || p.last_name) as person_name,"
            + " r.category, r.status, r.payment_status, r.amount, r.currency, r.guests, r.created_at, r.version";

    private EventDtos.RegistrationSummary summary(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new EventDtos.RegistrationSummary((UUID) rs.getObject("id"), (UUID) rs.getObject("event_id"), rs.getString("event_name"),
                (UUID) rs.getObject("person_id"), rs.getString("person_name"), rs.getString("category"), rs.getString("status"), rs.getString("payment_status"),
                rs.getBigDecimal("amount").toPlainString(), rs.getString("currency"), rs.getInt("guests"), rs.getTimestamp("created_at").toInstant(), rs.getLong("version"));
    }

    @Transactional(readOnly = true)
    public EventDtos.RegistrationDetail get(AccessScope scope, UUID id) {
        return build(load(scope, id));
    }

    RRow load(AccessScope scope, UUID id) {
        MapSqlParameterSource ps = new MapSqlParameterSource("id", id);
        String vis = EventSupport.visibleEvent(scope, ps, "e");
        return jdbc.query("select r.* from event_registration r join org_event e on e.id = r.event_id where r.id = :id and " + vis, ps, (rs, i) -> row(rs))
                .stream().findFirst().orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    private RRow lock(AccessScope scope, UUID id) {
        load(scope, id);
        return jdbc.query("select * from event_registration where id = :id for update", new MapSqlParameterSource("id", id), (rs, i) -> row(rs)).get(0);
    }

    private static RRow row(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new RRow((UUID) rs.getObject("id"), null, null, (UUID) rs.getObject("event_id"), (UUID) rs.getObject("person_id"), rs.getString("category"),
                rs.getString("status"), rs.getString("payment_status"), rs.getBigDecimal("amount"), rs.getString("currency"),
                (UUID) rs.getObject("submitted_by"), (UUID) rs.getObject("confirmed_by"), rs.getTimestamp("created_at").toInstant(), rs.getLong("version"));
    }

    private EventDtos.RegistrationDetail build(RRow r) {
        EventDtos.RegistrationSummary s = jdbc.query(SUMMARY + " from event_registration r join org_event e on e.id = r.event_id join person p on p.id = r.person_id"
                + " where r.id = :id", new MapSqlParameterSource("id", r.id()), (rs, i) -> summary(rs)).get(0);
        Map<String, Object> extra = jdbc.queryForMap("select answers::text as answers, ticket_code, ticket_used_at, payment_method, payment_reference, cancel_reason,"
                + " override_reason, guardian_person_id, financial_movement_id from event_registration where id = :id", new MapSqlParameterSource("id", r.id()));
        String answersJson = (String) extra.get("answers");
        Map<String, String> answers = answersJson == null ? Map.of() : readAnswers(answersJson);
        java.sql.Timestamp used = (java.sql.Timestamp) extra.get("ticket_used_at");
        UUID guardian = (UUID) extra.get("guardian_person_id");
        return new EventDtos.RegistrationDetail(s, answers, (String) extra.get("ticket_code"), used == null ? null : used.toInstant(),
                (String) extra.get("payment_method"), (String) extra.get("payment_reference"), (String) extra.get("cancel_reason"),
                (String) extra.get("override_reason"), guardian, support.personName(guardian), (UUID) extra.get("financial_movement_id"));
    }

    private Map<String, String> readAnswers(String json) {
        try {
            return mapper.readValue(json, ANSWERS);
        } catch (Exception e) {
            return Map.of();
        }
    }

    // ---------------------------------------------------------------- inscribir

    @Transactional
    public EventDtos.RegistrationDetail register(AuthenticatedActor actor, AccessScope scope, UUID eventId, EventDtos.RegisterRequest r) {
        authz.require(actor, MODULE, Action.C);
        UUID id = registerCore(scope, eventId, r, "STAFF", actor);
        return get(scope, id);
    }

    /** Núcleo de la inscripción, reusado por el formulario público (sin ventana con excepción: {@code allowOverride=false}). */
    UUID registerCore(AccessScope scope, UUID eventId, EventDtos.RegisterRequest r, String source, AuthenticatedActor actor) {
        boolean staff = "STAFF".equals(source);
        if (r == null || r.personId() == null || !EventSupport.hasText(r.category())) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "persona y categoría");
        }
        String category = r.category().trim().toUpperCase();
        if (!EventSupport.CATEGORIES.contains(category)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "categoría");
        }
        EventService.ERow e = events.load(scope, eventId);
        if (!"PUBLISHED".equals(e.status())) {
            throw new Exceptions("error.event.registrationClosed", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        support.assertPersonActive(e.orgId(), r.personId());                                                               // [V9]
        Instant now = support.now();
        boolean windowOpen = (e.regOpensAt() == null || !now.isBefore(e.regOpensAt())) && (e.regClosesAt() == null || !now.isAfter(e.regClosesAt()));
        boolean overrideUsed = false;
        if (!windowOpen) {
            if (!staff || !EventSupport.hasText(r.overrideReason())) {
                throw new Exceptions("error.event.registrationClosed", HttpStatus.UNPROCESSABLE_ENTITY);                   // [V13]
            }
            authz.require(actor, MODULE, Action.O);
            overrideUsed = true;
        }
        Integer minAge = e.minAge();
        UUID guardianId = r.guardianPersonId();
        if (minAge != null && minAge > 0) {
            LocalDate birth = personBirth(r.personId());
            int age = birth == null ? Integer.MAX_VALUE : Period.between(birth, LocalDate.now(clock)).getYears();
            if (age < minAge && guardianId == null) {
                throw new Exceptions("error.event.minAge", HttpStatus.UNPROCESSABLE_ENTITY, minAge);                       // [V11]
            }
        }
        int guests = r.guests() == null ? 0 : r.guests();
        if (guests > e.guestsMax()) {
            throw new Exceptions("error.event.guestsExceeded", HttpStatus.UNPROCESSABLE_ENTITY, e.guestsMax());            // [V12]
        }
        BigDecimal amount = tierAmount(eventId, category);
        String currency = tierCurrency(eventId, category);
        String status = "REGISTERED";
        if (events.registeredCount(eventId) >= (e.capacity() == null ? Integer.MAX_VALUE : e.capacity())) {
            if (!e.waitlistEnabled()) {
                throw new Exceptions("error.event.full", HttpStatus.UNPROCESSABLE_ENTITY);                                 // [V10]
            }
            status = "WAITLISTED";
        }
        UUID id = UUID.randomUUID();
        Timestamp now2 = Timestamp.from(now);
        UUID by = staff ? scope.personId() : null;
        try {
            jdbc.update("insert into event_registration (id, event_id, person_id, guardian_person_id, category, status, payment_status, amount, currency,"
                            + " guests, answers, override_reason, source, registered_by, created_at, created_by, updated_at, updated_by)"
                            + " values (:id, :e, :p, :g, :c, :s, :ps, :a, :cu, :gu, cast(:ans as jsonb), :or, :src, :by, :at, :by, :at, :by)",
                    new MapSqlParameterSource("id", id).addValue("e", eventId).addValue("p", r.personId()).addValue("g", guardianId).addValue("c", category)
                            .addValue("s", status).addValue("ps", amount.signum() == 0 ? "WAIVED" : "PENDING").addValue("a", amount).addValue("cu", currency)
                            .addValue("gu", guests).addValue("ans", writeAnswers(r.answers())).addValue("or", overrideUsed ? EventSupport.trim(r.overrideReason(), 300, "motivo") : null)
                            .addValue("src", source).addValue("by", by).addValue("at", now2));
        } catch (DataIntegrityViolationException ex) {
            throw new Exceptions("error.event.alreadyRegistered", HttpStatus.CONFLICT);                                    // [V9]
        }
        if ("REGISTERED".equals(status)) {
            issueTicket(id);
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("category", category);
        d.put("status", status);
        audit.record(new AuditService.Command(MODULE, "REGISTER", ENTITY, id, e.orgId(), e.branchId(), d));
        return id;
    }

    private LocalDate personBirth(UUID personId) {
        List<java.sql.Date> d = jdbc.query("select birth_date from person where id = :id", new MapSqlParameterSource("id", personId), (rs, i) -> rs.getDate(1));
        return d.isEmpty() || d.get(0) == null ? null : d.get(0).toLocalDate();
    }

    private BigDecimal tierAmount(UUID eventId, String category) {
        List<BigDecimal> a = jdbc.query("select amount from event_price_tier where event_id = :e and category = :c",
                new MapSqlParameterSource("e", eventId).addValue("c", category), (rs, i) -> rs.getBigDecimal(1));
        return a.isEmpty() ? BigDecimal.ZERO : a.get(0);                                                                   // [V8] sin tarifa = gratis
    }

    private String tierCurrency(UUID eventId, String category) {
        List<String> c = jdbc.query("select currency from event_price_tier where event_id = :e and category = :c",
                new MapSqlParameterSource("e", eventId).addValue("c", category), (rs, i) -> rs.getString(1));
        return c.isEmpty() ? "PEN" : c.get(0);
    }

    private String writeAnswers(Map<String, String> answers) {
        try {
            return mapper.writeValueAsString(answers == null ? Map.of() : answers);
        } catch (Exception e) {
            return "{}";
        }
    }

    private void issueTicket(UUID registrationId) {
        String code;
        int attempts = 0;
        do {
            code = EventSupport.ticketCode();
            attempts++;
        } while (attempts < 5 && jdbc.queryForObject("select count(*) from event_registration where ticket_code = :c", new MapSqlParameterSource("c", code), Integer.class) > 0);
        jdbc.update("update event_registration set ticket_code = :c where id = :id", new MapSqlParameterSource("c", code).addValue("id", registrationId));
    }

    // ---------------------------------------------------------------- cancelar y lista de espera

    /** Antes de cancelDeadline, libre. Después, exige la acción O con motivo [spec §N3]. PAID exige reembolso registrado [V16]. */
    @Transactional
    public EventDtos.RegistrationDetail cancelRegistration(AuthenticatedActor actor, AccessScope scope, UUID id, EventDtos.CancelRequest r) {
        authz.require(actor, MODULE, Action.S);
        RRow reg = lock(scope, id);
        if ("CANCELLED".equals(reg.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, reg.status());
        }
        EventService.ERow e = events.load(scope, reg.eventId());
        String reason = EventSupport.trim(r == null ? null : r.reason(), 500, "motivo");
        boolean withinDeadline = e.cancelDeadline() == null || !support.now().isAfter(e.cancelDeadline());
        if (!withinDeadline) {
            if (reason == null) {
                throw new Exceptions("error.event.cancelDeadline", HttpStatus.UNPROCESSABLE_ENTITY);                       // [spec] pasado el plazo exige aprobación
            }
            authz.require(actor, MODULE, Action.O);
        }
        if ("PAID".equals(reg.paymentStatus())) {
            if (r == null || !EventSupport.hasText(r.refundReference())) {
                throw new Exceptions("error.event.refundRequired", HttpStatus.UNPROCESSABLE_ENTITY);                       // [V16]
            }
            jdbc.update("update event_registration set payment_status = 'REFUNDED', payment_reference = :ref where id = :id",
                    new MapSqlParameterSource("ref", EventSupport.trim(r.refundReference(), 300, "referencia")).addValue("id", id));
        }
        boolean wasRegistered = "REGISTERED".equals(reg.status());
        jdbc.update("update event_registration set status = 'CANCELLED', cancel_reason = :r, updated_at = :at, updated_by = :by, version = version + 1 where id = :id",
                new MapSqlParameterSource("r", reason).addValue("at", Timestamp.from(support.now())).addValue("by", actor.ownerId()).addValue("id", id));
        audit.record(new AuditService.Command(MODULE, "CANCEL", ENTITY, id, e.orgId(), e.branchId(), Map.of("reason", String.valueOf(reason))));
        if (wasRegistered) {
            promoteOne(e);
        }
        return get(scope, id);
    }

    /** Promueve FIFO desde la lista de espera cuando se libera un cupo (una persona) [V3 T-C3]. */
    private void promoteOne(EventService.ERow e) {
        List<UUID> next = jdbc.query("select id from event_registration where event_id = :id and status = 'WAITLISTED' order by created_at limit 1",
                new MapSqlParameterSource("id", e.id()), (rs, i) -> (UUID) rs.getObject(1));
        if (next.isEmpty()) {
            return;
        }
        UUID regId = next.get(0);
        jdbc.update("update event_registration set status = 'REGISTERED', updated_at = :at, version = version + 1 where id = :id",
                new MapSqlParameterSource("at", Timestamp.from(support.now())).addValue("id", regId));
        issueTicket(regId);
        UUID personId = jdbc.queryForObject("select person_id from event_registration where id = :id", new MapSqlParameterSource("id", regId), UUID.class);
        notifications.toPersons(NotificationType.EVENT_WAITLIST_PROMOTED, e.orgId(), List.of(personId), Map.of("event", e.name()), "/app/events/" + e.id(), null);
        audit.record(new AuditService.Command(MODULE, "WAITLIST_PROMOTE", ENTITY, regId, e.orgId(), e.branchId(), Map.of()));
    }

    /** Promoción manual (p. ej. tras aumentar el cupo): promueve mientras haya lugar y lista de espera. */
    @Transactional
    public EventDtos.WaitlistPromoteResult promoteWaitlist(AuthenticatedActor actor, AccessScope scope, UUID eventId) {
        authz.require(actor, MODULE, Action.S);
        EventService.ERow e = events.load(scope, eventId);
        int promoted = 0;
        while (e.capacity() == null || events.registeredCount(eventId) < e.capacity()) {
            long before = events.registeredCount(eventId);
            promoteOne(e);
            if (events.registeredCount(eventId) == before) {
                break;
            }
            promoted++;
        }
        return new EventDtos.WaitlistPromoteResult(promoted);
    }

    // ---------------------------------------------------------------- pago (provisional hasta M15)

    @Transactional
    public EventDtos.RegistrationDetail submitPayment(AuthenticatedActor actor, AccessScope scope, UUID id, EventDtos.PaymentRequest r) {
        authz.require(actor, MODULE, Action.E);
        RRow reg = lock(scope, id);
        if (reg.amount().signum() == 0) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, "WAIVED");
        }
        if (!"PENDING".equals(reg.paymentStatus())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, reg.paymentStatus());
        }
        String method = r == null || !EventSupport.hasText(r.method()) ? null : r.method().trim().toUpperCase();
        if (method == null || !java.util.Set.of("CASH", "TRANSFER").contains(method)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "método de pago");
        }
        String reference = EventSupport.trim(r.reference(), 300, "referencia");
        jdbc.update("update event_registration set payment_method = :m, payment_reference = :ref, submitted_by = :by, updated_at = :at, version = version + 1 where id = :id",
                new MapSqlParameterSource("m", method).addValue("ref", reference).addValue("by", scope.personId()).addValue("at", Timestamp.from(support.now())).addValue("id", id));
        UUID[] ob = orgBranchOf(id);
        audit.record(new AuditService.Command(MODULE, "PAYMENT_SUBMIT", ENTITY, id, ob[0], ob[1], Map.of("method", method)));
        return get(scope, id);
    }

    private UUID[] orgBranchOf(UUID registrationId) {
        return jdbc.query("select e.organization_id, e.branch_id from event_registration r join org_event e on e.id = r.event_id where r.id = :id",
                new MapSqlParameterSource("id", registrationId), (rs, i) -> new UUID[]{(UUID) rs.getObject(1), (UUID) rs.getObject(2)}).get(0);
    }

    /**
     * Confirma el pago como PAID. Quien confirma no puede ser quien lo registró [T-C6]. [V32] Si el evento tiene fondo
     * configurado y la organización tiene FIN_MOVEMENTS contratado, además genera un ingreso PENDING real en M15
     * (categoría SERVICE_FEE, enlazado a {@code org_event.id}) que el equipo de finanzas aprueba desde su propia
     * pantalla — nunca se auto-aprueba aquí. Sin fondo configurado o sin el módulo contratado, el comportamiento es
     * exactamente el de antes: el pago queda solo como PAID dentro de esta tabla, sin ningún movimiento.
     */
    @Transactional
    public EventDtos.RegistrationDetail confirmPayment(AuthenticatedActor actor, AccessScope scope, UUID id) {
        authz.require(actor, MODULE, Action.E);
        RRow reg = lock(scope, id);
        if (!"PENDING".equals(reg.paymentStatus())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, reg.paymentStatus());
        }
        if (reg.submittedBy() != null && reg.submittedBy().equals(scope.personId())) {
            throw new Exceptions("error.common.selfApproval", HttpStatus.FORBIDDEN);                                       // [T-C6] auto-aprobación
        }
        Timestamp now = Timestamp.from(support.now());
        jdbc.update("update event_registration set payment_status = 'PAID', confirmed_by = :by, confirmed_at = :at, updated_at = :at, version = version + 1 where id = :id",
                new MapSqlParameterSource("by", scope.personId()).addValue("at", now).addValue("id", id));
        UUID[] ob = orgBranchOf(id);
        UUID finMovementId = tryCreateIncome(scope, reg, ob[0], ob[1]);
        if (finMovementId != null) {
            jdbc.update("update event_registration set financial_movement_id = :fm where id = :id",
                    new MapSqlParameterSource("fm", finMovementId).addValue("id", id));
        }
        audit.record(new AuditService.Command(MODULE, "PAYMENT_CONFIRM", ENTITY, id, ob[0], ob[1], Map.of()));
        EventDtos.RegistrationDetail out = get(scope, id);
        notifications.toPersons(NotificationType.EVENT_PAYMENT_CONFIRMED, ob[0], List.of(out.summary().personId()),
                Map.of("event", out.summary().eventName()), "/app/events/registrations/" + id, null);
        return out;
    }

    /**
     * [V32, cierra la mitad de M14 del D5 de M15] Ingreso PENDING idempotente por {@code (source_ref, source_id)}; null
     * (sin lanzar error) cuando el evento no tiene fondo configurado, cuando FIN_MOVEMENTS no está contratado, o cuando
     * el pago quedó en 0 (WAIVED nunca llega aquí, pero una tarifa GUEST/SCHOLARSHIP en 0 podría confirmarse igual).
     */
    private UUID tryCreateIncome(AccessScope scope, RRow reg, UUID orgId, UUID branchId) {
        if (reg.amount() == null || reg.amount().signum() <= 0 || !contractGate.enabled(orgId, FinanceSupport.MOD_MOVEMENTS)) {
            return null;
        }
        EventService.ERow event = events.load(scope, reg.eventId());
        if (event.fundId() == null) {
            return null;
        }
        FinancialMovementService finance = financeProvider.getObject();
        String description = "Inscripción a evento: " + event.name();
        return finance.createFromSource(orgId, branchId, support.today(), "INCOME", "SERVICE_FEE", event.fundId(), reg.amount(), description,
                event.id(), "EVENT_REGISTRATION", reg.id(), scope.personId());
    }

    // ---------------------------------------------------------------- check-in (núcleo M09, contexto EVENT)

    @Transactional
    public EventDtos.CheckinResult checkin(AuthenticatedActor actor, AccessScope scope, UUID eventId, EventDtos.CheckinRequest r) {
        authz.require(actor, MODULE, Action.T);
        EventService.ERow e = events.load(scope, eventId);
        RRow reg = findForCheckin(eventId, r);
        if (!"REGISTERED".equals(reg.status())) {
            throw new Exceptions("error.event.ticketInvalid", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        Instant now = support.now();
        if (now.isBefore(e.startAt().minus(Duration.ofHours(2))) || now.isAfter(e.endAt().plus(Duration.ofHours(2)))) {
            throw new Exceptions("error.event.outsideCheckinWindow", HttpStatus.UNPROCESSABLE_ENTITY);                     // [V14]
        }
        if (e.requirePaymentForCheckin() && !java.util.Set.of("PAID", "WAIVED").contains(reg.paymentStatus())) {
            throw new Exceptions("error.event.paymentPending", HttpStatus.UNPROCESSABLE_ENTITY);                           // [T-C8]
        }
        boolean alreadyUsed = jdbc.queryForObject("select ticket_used_at is not null from event_registration where id = :id",
                new MapSqlParameterSource("id", reg.id()), Boolean.class);
        if (alreadyUsed) {
            throw new Exceptions("error.event.ticketUsed", HttpStatus.UNPROCESSABLE_ENTITY);                               // [V14]
        }
        jdbc.update("update event_registration set ticket_used_at = :at where id = :id", new MapSqlParameterSource("at", Timestamp.from(now)).addValue("id", reg.id()));
        UUID sessionId = attendance.ensureContextSession(e.orgId(), e.branchId(), CONTEXT, eventId, e.name(), LocalDate.now(support.zoneOf(e.branchId(), e.orgId())),
                e.startAt().atZone(support.zoneOf(e.branchId(), e.orgId())).toLocalTime(), (int) Math.max(15, Duration.between(e.startAt(), e.endAt()).toMinutes()), actor.ownerId());
        attendance.record(actor, scope, sessionId, new AttendanceDtos.RecordRequest(reg.personId(), "PRESENT", "QR"));
        audit.record(new AuditService.Command(MODULE, "CHECKIN", ENTITY, reg.id(), e.orgId(), e.branchId(), Map.of()));
        return new EventDtos.CheckinResult(get(scope, reg.id()).summary(), "ok.event.checkedIn");
    }

    private RRow findForCheckin(UUID eventId, EventDtos.CheckinRequest r) {
        if (r != null && EventSupport.hasText(r.ticketCode())) {
            List<RRow> found = jdbc.query("select * from event_registration where ticket_code = :c", new MapSqlParameterSource("c", r.ticketCode().trim().toUpperCase()), (rs, i) -> row(rs));
            if (found.isEmpty() || !found.get(0).eventId().equals(eventId)) {
                throw new Exceptions("error.event.ticketInvalid", HttpStatus.UNPROCESSABLE_ENTITY);                        // ticket de otro evento
            }
            return found.get(0);
        }
        if (r != null && r.personId() != null) {
            List<RRow> found = jdbc.query("select * from event_registration where event_id = :e and person_id = :p and status <> 'CANCELLED'",
                    new MapSqlParameterSource("e", eventId).addValue("p", r.personId()), (rs, i) -> row(rs));
            if (found.isEmpty()) {
                throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
            }
            return found.get(0);
        }
        throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "ticket o persona");
    }
}
