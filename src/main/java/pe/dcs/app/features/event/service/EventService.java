package pe.dcs.app.features.event.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.event.dto.EventDtos;
import pe.dcs.app.features.facility.service.FacilitySupport;
import pe.dcs.app.features.facility.service.ReservationService;
import pe.dcs.app.features.facility.service.SpaceService;
import pe.dcs.app.features.finance.service.FundService;
import pe.dcs.app.features.notification.domain.NotificationType;
import pe.dcs.app.features.notification.service.NotificationService;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.AuthorizationService;
import pe.dcs.app.security.authz.ContractGate;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.RoleType;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PaginationResponse;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * M14 · Evento: DRAFT → PUBLISHED [V3] → FINISHED (al pasar la fecha o manual) | CANCELLED (motivo si hay inscritos [V7]).
 * Solo ORG_ADMIN crea eventos de organización [D del spec]; ORG_BRANCH_ADMIN administra los de su sede y gestiona inscritos de
 * su sede en eventos de organización. El pago vive en event_registration; desde [V32] la confirmación de pago genera además
 * un ingreso real en M15 cuando el evento tiene fondo configurado y FIN_MOVEMENTS está contratado (ver
 * {@code EventRegistrationService.confirmPayment}) — el fondo se configura aquí, en el evento, y es siempre opcional.
 * Desde [V33] la publicación genera además, opcionalmente, una reserva real en M16 cuando el evento tiene espacio
 * configurado y SPACES está contratado (ver {@link #publish}, {@link #update} y {@link #cancel}).
 */
@Service
@RequiredArgsConstructor
public class EventService {

    static final String MODULE = EventSupport.MODULE;
    private static final String ENTITY = "Event";
    private static final String RESERVATION_SOURCE = "EVENT";
    private static final Set<String> STATUSES = Set.of("DRAFT", "PUBLISHED", "FINISHED", "CANCELLED");

    private final NamedParameterJdbcTemplate jdbc;
    private final EventSupport support;
    private final NotificationService notifications;
    private final AuthorizationService authz;
    private final ObjectProvider<FundService> financeProvider;
    private final ObjectProvider<SpaceService> spaceProvider;
    private final ObjectProvider<ReservationService> reservationProvider;
    private final ContractGate contractGate;
    private final AuditService audit;
    private final Clock clock;

    record ERow(UUID id, UUID orgId, UUID branchId, String scope, String name, Instant startAt, Instant endAt, String location, String onlineUrl,
               Integer capacity, boolean waitlistEnabled, Instant regOpensAt, Instant regClosesAt, Instant cancelDeadline, boolean isPublic,
               Integer minAge, int guestsMax, boolean requirePaymentForCheckin, UUID fundId, UUID spaceId, String status, long version) {
    }

    // ---------------------------------------------------------------- consulta

    @Transactional(readOnly = true)
    public PageResponse<EventDtos.EventSummary> search(AccessScope scope, EventDtos.EventSearch req) {
        EventDtos.EventSearch.EventFilters f = req == null || req.filters() == null
                ? new EventDtos.EventSearch.EventFilters(null, null, null, null, null) : req.filters();
        MapSqlParameterSource ps = new MapSqlParameterSource();
        StringBuilder w = new StringBuilder(EventSupport.visibleEvent(scope, ps, "e"));
        if (f.branchId() != null) {
            w.append(" and e.branch_id = :fb");
            ps.addValue("fb", f.branchId());
        }
        if (EventSupport.hasText(f.status())) {
            String s = f.status().trim().toUpperCase();
            if (!STATUSES.contains(s)) {
                throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "estado");
            }
            w.append(" and e.status = :fs");
            ps.addValue("fs", s);
        }
        if (EventSupport.hasText(f.scope())) {
            w.append(" and e.scope = :fsc");
            ps.addValue("fsc", f.scope().trim().toUpperCase());
        }
        if (f.from() != null) {
            w.append(" and e.end_at >= :df");
            ps.addValue("df", Timestamp.from(f.from()));
        }
        if (f.to() != null) {
            w.append(" and e.start_at <= :dt");
            ps.addValue("dt", Timestamp.from(f.to()));
        }
        String from = " from org_event e left join branch b on b.id = e.branch_id where ";
        Long total = jdbc.queryForObject("select count(*)" + from + w, ps, Long.class);
        int size = req == null || req.pagination() == null ? 20 : Math.max(1, Math.min(req.pagination().getSize(), 200));
        int page = req == null || req.pagination() == null ? 0 : Math.max(0, req.pagination().getPage());
        ps.addValue("lim", size).addValue("off", (long) page * size);
        List<EventDtos.EventSummary> rows = jdbc.query(SUMMARY + from + w + " order by e.start_at desc, e.id limit :lim offset :off", ps, (rs, i) -> summary(rs));
        long t = total == null ? 0 : total;
        return new PageResponse<>(rows, new PaginationResponse((int) t, (int) Math.ceil(t / (double) size), size, page));
    }

    private static final String SUMMARY = "select e.id, e.name, e.scope, e.branch_id, b.name as branch_name, e.type_code, e.start_at, e.end_at, e.location,"
            + " e.online_url, e.capacity, e.is_public, e.status, e.created_at, e.version,"
            + " (select count(*) from event_registration r where r.event_id = e.id and r.status = 'REGISTERED') as registered,"
            + " (select count(*) from event_registration r where r.event_id = e.id and r.status = 'WAITLISTED') as waitlisted";

    private EventDtos.EventSummary summary(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new EventDtos.EventSummary((UUID) rs.getObject("id"), rs.getString("name"), rs.getString("scope"), (UUID) rs.getObject("branch_id"),
                rs.getString("branch_name"), rs.getString("type_code"), rs.getTimestamp("start_at").toInstant(), rs.getTimestamp("end_at").toInstant(),
                rs.getString("location"), rs.getString("online_url"), (Integer) rs.getObject("capacity"), rs.getInt("registered"), rs.getInt("waitlisted"),
                rs.getString("status"), rs.getBoolean("is_public"), rs.getTimestamp("created_at").toInstant(), rs.getLong("version"));
    }

    @Transactional(readOnly = true)
    public EventDtos.EventDetail get(AccessScope scope, UUID id) {
        return build(load(scope, id));
    }

    ERow load(AccessScope scope, UUID id) {
        MapSqlParameterSource ps = new MapSqlParameterSource("id", id);
        String vis = EventSupport.visibleEvent(scope, ps, "e");
        return jdbc.query("select e.* from org_event e where e.id = :id and " + vis, ps, (rs, i) -> row(rs)).stream().findFirst()
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    private ERow lock(AccessScope scope, UUID id) {
        load(scope, id);
        return jdbc.query("select * from org_event where id = :id for update", new MapSqlParameterSource("id", id), (rs, i) -> row(rs)).get(0);
    }

    private static ERow row(java.sql.ResultSet rs) throws java.sql.SQLException {
        java.sql.Timestamp ro = rs.getTimestamp("reg_opens_at");
        java.sql.Timestamp rc = rs.getTimestamp("reg_closes_at");
        java.sql.Timestamp cd = rs.getTimestamp("cancel_deadline");
        return new ERow((UUID) rs.getObject("id"), (UUID) rs.getObject("organization_id"), (UUID) rs.getObject("branch_id"), rs.getString("scope"),
                rs.getString("name"), rs.getTimestamp("start_at").toInstant(), rs.getTimestamp("end_at").toInstant(), rs.getString("location"),
                rs.getString("online_url"), (Integer) rs.getObject("capacity"), rs.getBoolean("waitlist_enabled"), ro == null ? null : ro.toInstant(),
                rc == null ? null : rc.toInstant(), cd == null ? null : cd.toInstant(), rs.getBoolean("is_public"), (Integer) rs.getObject("min_age"),
                rs.getInt("guests_max"), rs.getBoolean("require_payment_for_checkin"), (UUID) rs.getObject("fund_id"), (UUID) rs.getObject("space_id"),
                rs.getString("status"), rs.getLong("version"));
    }

    private EventDtos.EventDetail build(ERow e) {
        EventDtos.EventSummary s = jdbc.query(SUMMARY + " from org_event e left join branch b on b.id = e.branch_id where e.id = :id",
                new MapSqlParameterSource("id", e.id()), (rs, i) -> summary(rs)).get(0);
        Map<String, Object> extra = jdbc.queryForMap("select ev.description, ev.banner_url, ev.cancel_reason, f.name as fund_name, sp.name as space_name"
                        + " from org_event ev left join fin_fund f on f.id = ev.fund_id left join space sp on sp.id = ev.space_id where ev.id = :id",
                new MapSqlParameterSource("id", e.id()));
        List<EventDtos.PriceTier> tiers = jdbc.query("select category, amount, currency from event_price_tier where event_id = :id order by category",
                new MapSqlParameterSource("id", e.id()), (rs, i) -> new EventDtos.PriceTier(rs.getString(1), rs.getBigDecimal(2).toPlainString(), rs.getString(3)));
        List<EventDtos.QuestionView> questions = jdbc.query("select id, label, type, options, required, sort_order from event_question where event_id = :id order by sort_order",
                new MapSqlParameterSource("id", e.id()), (rs, i) -> new EventDtos.QuestionView((UUID) rs.getObject(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getBoolean(5), rs.getInt(6)));
        return new EventDtos.EventDetail(s, (String) extra.get("description"), (String) extra.get("banner_url"), e.waitlistEnabled(), e.regOpensAt(), e.regClosesAt(),
                e.cancelDeadline(), false, e.minAge(), e.guestsMax(), e.requirePaymentForCheckin(), (String) extra.get("cancel_reason"), e.fundId(),
                (String) extra.get("fund_name"), e.spaceId(), (String) extra.get("space_name"), tiers, questions);
    }

    // ---------------------------------------------------------------- alta y edición

    @Transactional
    public EventDtos.EventDetail create(AuthenticatedActor actor, AccessScope scope, EventDtos.EventRequest r) {
        authz.require(actor, MODULE, Action.C);
        validateBase(r, null);
        validateFund(scope.organizationId(), r.fundId());
        String eventScope = r.scope() == null ? "BRANCH" : r.scope().trim().toUpperCase();
        UUID branchId;
        if ("ORGANIZATION".equals(eventScope)) {
            if (scope.role() != RoleType.ORG_ADMIN) {
                throw new Exceptions("error.event.scopeDenied", HttpStatus.FORBIDDEN);                                     // [T01] solo ORG_ADMIN
            }
            branchId = null;
        } else if ("BRANCH".equals(eventScope)) {
            branchId = r.branchId();
            if (branchId == null || !scope.canSeeBranch(branchId)) {
                throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "sede");
            }
        } else {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "alcance");
        }
        validateSpace(scope.organizationId(), eventScope, branchId, r.spaceId());
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("insert into org_event (id, organization_id, scope, branch_id, name, type_code, description, start_at, end_at, location, online_url,"
                        + " banner_url, capacity, waitlist_enabled, reg_opens_at, reg_closes_at, cancel_deadline, is_public, requires_approval, min_age,"
                        + " guests_max, require_payment_for_checkin, fund_id, space_id, status, created_at, created_by, updated_at, updated_by)"
                        + " values (:id, :o, :sc, :b, :n, :ty, :d, :sa, :ea, :loc, :url, :ban, :cap, :wl, :ro, :rc, :cd, :pub, :req, :age, :gm, :pay, :fund,"
                        + " :space, 'DRAFT', :at, :by, :at, :by)",
                params(id, scope.organizationId(), eventScope, branchId, r, now, actor.ownerId()));
        audit.record(new AuditService.Command(MODULE, "CREATE", ENTITY, id, scope.organizationId(), branchId, Map.of("name", r.name())));
        return get(scope, id);
    }

    @Transactional
    public EventDtos.EventDetail update(AuthenticatedActor actor, AccessScope scope, UUID id, EventDtos.EventRequest r) {
        authz.require(actor, MODULE, Action.E);
        ERow e = lock(scope, id);
        if ("CANCELLED".equals(e.status()) || "FINISHED".equals(e.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, e.status());
        }
        validateBase(r, e);
        validateFund(e.orgId(), r.fundId());
        validateSpace(e.orgId(), e.scope(), e.branchId(), r.spaceId());
        if (r.capacity() != null) {
            int active = registeredCount(id);
            if (r.capacity() < active) {
                throw new Exceptions("error.event.capacityBelowRegistered", HttpStatus.UNPROCESSABLE_ENTITY, active);      // [V4]
            }
        }
        int n = jdbc.update("update org_event set name = :n, type_code = :ty, description = :d, start_at = :sa, end_at = :ea, location = :loc,"
                        + " online_url = :url, banner_url = :ban, capacity = :cap, waitlist_enabled = :wl, reg_opens_at = :ro, reg_closes_at = :rc,"
                        + " cancel_deadline = :cd, is_public = :pub, requires_approval = :req, min_age = :age, guests_max = :gm,"
                        + " require_payment_for_checkin = :pay, fund_id = :fund, space_id = :space, updated_at = :at, updated_by = :by, version = version + 1"
                        + " where id = :id and (cast(:v as bigint) is null or version = :v)",
                params(id, e.orgId(), e.scope(), e.branchId(), r, Timestamp.from(clock.instant()), actor.ownerId()).addValue("v", r.version()));
        if (n == 0) {
            throw new Exceptions("error.common.concurrentUpdate", HttpStatus.CONFLICT);
        }
        // [V33] Si el evento ya está PUBLISHED y cambia el espacio o las fechas, la reserva enlazada (si la había) se
        // reordena: se cancela la anterior y, si sigue habiendo espacio configurado y SPACES contratado, se crea una
        // nueva con los datos vigentes. Sin espacio antes ni después, no se toca nada (evita consultas de más).
        if ("PUBLISHED".equals(e.status()) && (e.spaceId() != null || r.spaceId() != null)) {
            boolean changed = !java.util.Objects.equals(e.spaceId(), r.spaceId()) || !e.startAt().equals(r.startAt()) || !e.endAt().equals(r.endAt());
            if (changed) {
                if (e.spaceId() != null) {
                    reservationProvider.getObject().cancelBySource(e.orgId(), RESERVATION_SOURCE, id, "Evento actualizado");
                }
                if (r.spaceId() != null && contractGate.enabled(e.orgId(), FacilitySupport.MOD_SPACES)) {
                    reservationProvider.getObject().createLinked(e.orgId(), r.spaceId(), actor.ownerId(), r.name().trim(), RESERVATION_SOURCE, id, r.startAt(), r.endAt());
                }
            }
        }
        audit.record(new AuditService.Command(MODULE, "UPDATE", ENTITY, id, e.orgId(), e.branchId(), Map.of()));
        return get(scope, id);
    }

    /**
     * [V32] El fondo es siempre opcional (un evento gratuito o uno cuya organización no usa Finanzas simplemente no lo
     * configura). Si se envía uno, debe existir y estar ACTIVE en esta organización — mismo criterio que M16/M17 exigen
     * al elegir fondo. No exige que FIN_MOVEMENTS esté contratado: eso solo decide si, al confirmar un pago, el ingreso
     * llega a crearse (ver {@code EventRegistrationService.confirmPayment}); el fondo puede quedar configurado de
     * antemano aunque el contrato aún no incluya el módulo.
     */
    private void validateFund(UUID orgId, UUID fundId) {
        if (fundId == null) {
            return;
        }
        FundService.FundFacts fund = financeProvider.getObject().facts(orgId, fundId);
        if (!"ACTIVE".equals(fund.status())) {
            throw new Exceptions("error.finance.fundInactive", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (fund.allowedCategories() != null && !fund.allowedCategories().contains("SERVICE_FEE")) {
            throw new Exceptions("error.finance.categoryNotAllowed", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    /**
     * [V33] El espacio también es siempre opcional, igual que el fondo. Solo tiene sentido en eventos de alcance
     * BRANCH (uno de ORGANIZATION no tiene una sede fija con la que casar un espacio) y debe pertenecer a esa misma
     * sede y estar ACTIVE — mismo criterio que exige {@code ReservationService} para una reserva manual. No exige que
     * SPACES esté contratado: eso solo decide si, al publicar, la reserva llega a crearse (ver {@link #publish}).
     */
    private void validateSpace(UUID orgId, String eventScope, UUID branchId, UUID spaceId) {
        if (spaceId == null) {
            return;
        }
        if (!"BRANCH".equals(eventScope) || branchId == null) {
            throw new Exceptions("error.event.spaceRequiresBranch", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        SpaceService.SpaceFacts space = spaceProvider.getObject().facts(orgId, spaceId);
        if (!"ACTIVE".equals(space.status())) {
            throw new Exceptions("error.space.inactive", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (!branchId.equals(space.branchId())) {
            throw new Exceptions("error.space.branchMismatch", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private void validateBase(EventDtos.EventRequest r, ERow existing) {
        if (r == null || !EventSupport.hasText(r.name())) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "nombre");
        }
        String name = r.name().trim();
        if (name.length() < 3 || name.length() > 120) {
            throw new Exceptions("error.event.nameLength", HttpStatus.BAD_REQUEST);                                        // [V1]
        }
        if (r.startAt() == null || r.endAt() == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "fechas");
        }
        if (!r.endAt().isAfter(r.startAt())) {
            throw new Exceptions("error.event.endBeforeStart", HttpStatus.BAD_REQUEST);                                    // [V2]
        }
        if (r.regOpensAt() != null && r.regClosesAt() != null && r.regOpensAt().isAfter(r.regClosesAt())) {
            throw new Exceptions("error.event.windowInvalid", HttpStatus.BAD_REQUEST);                                     // [V5]
        }
        if (r.regClosesAt() != null && r.regClosesAt().isAfter(r.startAt())) {
            throw new Exceptions("error.event.windowInvalid", HttpStatus.BAD_REQUEST);                                     // [V5]
        }
        if (r.capacity() != null && r.capacity() < 1) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "cupo");
        }
        if (r.guestsMax() != null && r.guestsMax() < 0) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "invitados máximos");
        }
    }

    private MapSqlParameterSource params(UUID id, UUID orgId, String eventScope, UUID branchId, EventDtos.EventRequest r, Timestamp at, UUID by) {
        return new MapSqlParameterSource("id", id).addValue("o", orgId).addValue("sc", eventScope).addValue("b", branchId)
                .addValue("n", r.name().trim()).addValue("ty", EventSupport.trim(r.typeCode(), 40, "tipo"))
                .addValue("d", EventSupport.trim(r.description(), 2000, "descripción")).addValue("sa", Timestamp.from(r.startAt()))
                .addValue("ea", Timestamp.from(r.endAt())).addValue("loc", EventSupport.trim(r.location(), 300, "lugar"))
                .addValue("url", EventSupport.trim(r.onlineUrl(), 500, "enlace")).addValue("ban", EventSupport.trim(r.bannerUrl(), 500, "banner"))
                .addValue("cap", r.capacity()).addValue("wl", Boolean.TRUE.equals(r.waitlistEnabled()))
                .addValue("ro", r.regOpensAt() == null ? null : Timestamp.from(r.regOpensAt())).addValue("rc", r.regClosesAt() == null ? null : Timestamp.from(r.regClosesAt()))
                .addValue("cd", r.cancelDeadline() == null ? null : Timestamp.from(r.cancelDeadline())).addValue("pub", Boolean.TRUE.equals(r.isPublic()))
                .addValue("req", Boolean.TRUE.equals(r.requiresApproval())).addValue("age", r.minAge()).addValue("gm", r.guestsMax() == null ? 0 : r.guestsMax())
                .addValue("pay", Boolean.TRUE.equals(r.requirePaymentForCheckin())).addValue("fund", r.fundId()).addValue("space", r.spaceId())
                .addValue("at", at).addValue("by", by);
    }

    @Transactional
    public void delete(AuthenticatedActor actor, AccessScope scope, UUID id) {
        authz.require(actor, MODULE, Action.D);
        ERow e = lock(scope, id);
        if (!"DRAFT".equals(e.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, e.status());
        }
        jdbc.update("delete from event_question where event_id = :id", new MapSqlParameterSource("id", id));
        jdbc.update("delete from event_price_tier where event_id = :id", new MapSqlParameterSource("id", id));
        jdbc.update("delete from org_event where id = :id", new MapSqlParameterSource("id", id));
        audit.record(new AuditService.Command(MODULE, "DELETE", ENTITY, id, e.orgId(), e.branchId(), Map.of("name", e.name())));
    }

    // ---------------------------------------------------------------- tarifas y preguntas (solo en DRAFT o PUBLISHED sin afectar inscritos [V8])

    @Transactional
    public EventDtos.EventDetail setPriceTiers(AuthenticatedActor actor, AccessScope scope, UUID id, List<EventDtos.PriceTier> tiers) {
        authz.require(actor, MODULE, Action.E);
        ERow e = lock(scope, id);
        if ("CANCELLED".equals(e.status()) || "FINISHED".equals(e.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, e.status());
        }
        jdbc.update("delete from event_price_tier where event_id = :id", new MapSqlParameterSource("id", id));
        for (EventDtos.PriceTier t : tiers == null ? List.<EventDtos.PriceTier>of() : tiers) {
            if (t.category() == null || !EventSupport.CATEGORIES.contains(t.category().trim().toUpperCase())) {
                throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "categoría");
            }
            BigDecimal amount = number(t.amount());
            if (amount.signum() < 0) {
                throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "monto");                             // [V6]
            }
            jdbc.update("insert into event_price_tier (id, event_id, category, amount, currency) values (:id, :e, :c, :a, :cu)",
                    new MapSqlParameterSource("id", UUID.randomUUID()).addValue("e", id).addValue("c", t.category().trim().toUpperCase())
                            .addValue("a", amount).addValue("cu", EventSupport.hasText(t.currency()) ? t.currency().trim().toUpperCase() : "PEN"));
        }
        audit.record(new AuditService.Command(MODULE, "PRICE_TIERS", ENTITY, id, e.orgId(), e.branchId(), Map.of("tiers", tiers == null ? 0 : tiers.size())));
        return get(scope, id);
    }

    private static BigDecimal number(String s) {
        if (s == null || s.isBlank()) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "monto");
        }
        try {
            return new BigDecimal(s.trim());
        } catch (NumberFormatException e) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "monto");
        }
    }

    @Transactional
    public EventDtos.EventDetail addQuestion(AuthenticatedActor actor, AccessScope scope, UUID id, EventDtos.QuestionRequest r) {
        authz.require(actor, MODULE, Action.E);
        ERow e = lock(scope, id);
        String label = r == null ? null : EventSupport.trim(r.label(), 200, "pregunta");
        if (label == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "pregunta");
        }
        String type = r.type() == null ? "TEXT" : r.type().trim().toUpperCase();
        if (!Set.of("TEXT", "CHOICE", "BOOL").contains(type)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "tipo de pregunta");
        }
        Integer nextOrder = jdbc.queryForObject("select coalesce(max(sort_order), -1) + 1 from event_question where event_id = :id", new MapSqlParameterSource("id", id), Integer.class);
        jdbc.update("insert into event_question (id, event_id, label, type, options, required, sort_order) values (:id, :e, :l, :t, :o, :r, :so)",
                new MapSqlParameterSource("id", UUID.randomUUID()).addValue("e", id).addValue("l", label).addValue("t", type)
                        .addValue("o", EventSupport.trim(r.options(), 500, "opciones")).addValue("r", Boolean.TRUE.equals(r.required()))
                        .addValue("so", r.sortOrder() == null ? nextOrder : r.sortOrder()));
        audit.record(new AuditService.Command(MODULE, "QUESTION_ADD", ENTITY, id, e.orgId(), e.branchId(), Map.of("label", label)));
        return get(scope, id);
    }

    @Transactional
    public EventDtos.EventDetail removeQuestion(AuthenticatedActor actor, AccessScope scope, UUID id, UUID questionId) {
        authz.require(actor, MODULE, Action.E);
        ERow e = lock(scope, id);
        jdbc.update("delete from event_question where id = :qid and event_id = :id", new MapSqlParameterSource("qid", questionId).addValue("id", id));
        audit.record(new AuditService.Command(MODULE, "QUESTION_REMOVE", ENTITY, id, e.orgId(), e.branchId(), Map.of()));
        return get(scope, id);
    }

    /** /admin/event-questions: crea resolviendo el evento desde el cuerpo. */
    @Transactional
    public EventDtos.EventDetail addQuestion(AuthenticatedActor actor, AccessScope scope, EventDtos.QuestionCreateRequest r) {
        if (r == null || r.eventId() == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "evento");
        }
        return addQuestion(actor, scope, r.eventId(), new EventDtos.QuestionRequest(r.label(), r.type(), r.options(), r.required(), r.sortOrder()));
    }

    /** /admin/event-questions/{id}: borra resolviendo el evento desde la propia pregunta. */
    @Transactional
    public void removeQuestionById(AuthenticatedActor actor, AccessScope scope, UUID questionId) {
        List<UUID> eventId = jdbc.query("select event_id from event_question where id = :id", new MapSqlParameterSource("id", questionId), (rs, i) -> (UUID) rs.getObject(1));
        if (eventId.isEmpty()) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        removeQuestion(actor, scope, eventId.get(0), questionId);
    }

    // ---------------------------------------------------------------- ciclo de vida

    /** [V3] fecha futura y lugar u online. */
    @Transactional
    public EventDtos.EventDetail publish(AuthenticatedActor actor, AccessScope scope, UUID id) {
        authz.require(actor, MODULE, Action.P);
        ERow e = lock(scope, id);
        if (!"DRAFT".equals(e.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, e.status());
        }
        boolean hasPlace = EventSupport.hasText(e.location()) || EventSupport.hasText(e.onlineUrl());
        if (!e.startAt().isAfter(clock.instant()) || !hasPlace) {
            throw new Exceptions("error.event.publishRequirements", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        jdbc.update("update org_event set status = 'PUBLISHED', updated_at = :at, updated_by = :by, version = version + 1 where id = :id",
                new MapSqlParameterSource("at", Timestamp.from(clock.instant())).addValue("by", actor.ownerId()).addValue("id", id));
        // [V33] Reserva real en M16 solo si el evento tiene espacio configurado y la organización tiene SPACES
        // contratado; si el espacio ya está ocupado en ese horario, createLinked() lanza 409 y la publicación falla
        // completa (rollback), igual que si faltara lugar o fecha futura.
        if (e.spaceId() != null && contractGate.enabled(e.orgId(), FacilitySupport.MOD_SPACES)) {
            reservationProvider.getObject().createLinked(e.orgId(), e.spaceId(), actor.ownerId(), e.name(), RESERVATION_SOURCE, id, e.startAt(), e.endAt());
        }
        audit.record(new AuditService.Command(MODULE, "PUBLISH", ENTITY, id, e.orgId(), e.branchId(), Map.of("name", e.name())));
        return get(scope, id);
    }

    @Transactional
    public EventDtos.EventDetail cancel(AuthenticatedActor actor, AccessScope scope, UUID id, String reasonText) {
        authz.require(actor, MODULE, Action.S);
        ERow e = lock(scope, id);
        if ("CANCELLED".equals(e.status()) || "FINISHED".equals(e.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, e.status());
        }
        int active = registeredCount(id);
        String reason = EventSupport.trim(reasonText, 500, "motivo");
        if (active > 0 && reason == null) {
            throw new Exceptions("error.common.reasonRequired", HttpStatus.BAD_REQUEST);                                   // [V7]
        }
        jdbc.update("update org_event set status = 'CANCELLED', cancel_reason = :r, updated_at = :at, updated_by = :by, version = version + 1 where id = :id",
                new MapSqlParameterSource("r", reason).addValue("at", Timestamp.from(clock.instant())).addValue("by", actor.ownerId()).addValue("id", id));
        // Reembolsos: quedan pendientes en la propia inscripción (provisional hasta M15); ver EventRegistrationService.
        List<UUID> people = jdbc.query("select person_id from event_registration where event_id = :id and status <> 'CANCELLED'",
                new MapSqlParameterSource("id", id), (rs, i) -> (UUID) rs.getObject(1));
        jdbc.update("update event_registration set status = 'CANCELLED', cancel_reason = :r, updated_at = :at, version = version + 1"
                        + " where event_id = :id and status <> 'CANCELLED'",
                new MapSqlParameterSource("r", "Evento cancelado: " + reason).addValue("at", Timestamp.from(clock.instant())).addValue("id", id));
        if (!people.isEmpty()) {
            notifications.toPersons(NotificationType.EVENT_CANCELLED, e.orgId(), people, Map.of("event", e.name()), "/app/events/" + id, null);
        }
        if (e.spaceId() != null) {
            reservationProvider.getObject().cancelBySource(e.orgId(), RESERVATION_SOURCE, id, "Evento cancelado" + (reason == null ? "" : ": " + reason));
        }
        audit.record(new AuditService.Command(MODULE, "CANCEL", ENTITY, id, e.orgId(), e.branchId(), Map.of("reason", reason, "affected", people.size())));
        return get(scope, id);
    }

    @Transactional
    public EventDtos.EventDetail finish(AuthenticatedActor actor, AccessScope scope, UUID id) {
        authz.require(actor, MODULE, Action.S);
        ERow e = lock(scope, id);
        if (!"PUBLISHED".equals(e.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, e.status());
        }
        jdbc.update("update org_event set status = 'FINISHED', updated_at = :at, updated_by = :by, version = version + 1 where id = :id",
                new MapSqlParameterSource("at", Timestamp.from(clock.instant())).addValue("by", actor.ownerId()).addValue("id", id));
        audit.record(new AuditService.Command(MODULE, "FINISH", ENTITY, id, e.orgId(), e.branchId(), Map.of()));
        return get(scope, id);
    }

    /** Lo llama el job (SYSTEM) para los eventos publicados cuya fecha de fin ya pasó. */
    @Transactional
    public int autoFinishDue() {
        List<UUID> due = jdbc.query("select id from org_event where status = 'PUBLISHED' and end_at < :now",
                new MapSqlParameterSource("now", Timestamp.from(clock.instant())), (rs, i) -> (UUID) rs.getObject(1));
        for (UUID id : due) {
            jdbc.update("update org_event set status = 'FINISHED', updated_at = :at, version = version + 1 where id = :id",
                    new MapSqlParameterSource("at", Timestamp.from(clock.instant())).addValue("id", id));
            audit.record(new AuditService.Command(MODULE, "FINISH", ENTITY, id, null, null, Map.of("auto", true)));
        }
        return due.size();
    }

    // ---------------------------------------------------------------- apoyo para EventRegistrationService

    int registeredCount(UUID eventId) {
        Integer n = jdbc.queryForObject("select count(*) from event_registration where event_id = :id and status = 'REGISTERED'",
                new MapSqlParameterSource("id", eventId), Integer.class);
        return n == null ? 0 : n;
    }

    String catalogTypeName(UUID orgId, String code) {
        if (code == null) {
            return null;
        }
        List<String> n = jdbc.queryForList("select name_es from catalog_item where type = 'EVENT_TYPE' and code = :c and (organization_id is null or organization_id = :o)",
                new MapSqlParameterSource("c", code).addValue("o", orgId), String.class);
        return n.isEmpty() ? code : n.get(0);
    }
}
