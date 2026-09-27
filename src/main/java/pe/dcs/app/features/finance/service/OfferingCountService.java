package pe.dcs.app.features.finance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.finance.dto.FinanceDtos;
import pe.dcs.app.features.person.service.PersonLookupService;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.AuthorizationService;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PaginationResponse;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M15 · Conteo de ofrenda [V13]: dos contadores distintos, ambos confirman ({@code POST .../confirm}, llamado una vez por
 * cada uno; el primero que llega marca su columna, 403 si quien llama no es ninguno de los dos) y solo entonces se generan
 * los movimientos ya conciliados (uno por fondo asignado, APPROVED de una vez: nace confirmado por partida doble, así que no
 * pasa por el ciclo de aprobación normal).
 * <p>Fecha del movimiento generado: la de la sesión de culto ({@code session_id} → {@code attendance_session.session_date})
 * si se indicó una, o si no la fecha de hoy (zona de la sede) al momento de la confirmación — decisión propia, documentada
 * porque {@code fin_offering_count} no tiene una columna de fecha propia en la migración.
 */
@Service
@RequiredArgsConstructor
public class OfferingCountService {

    private static final TypeReference<Map<String, Integer>> BREAKDOWN = new TypeReference<>() { };
    private static final String ENTITY = "OfferingCount";

    private final NamedParameterJdbcTemplate jdbc;
    private final AuthorizationService authz;
    private final FinanceSupport support;
    private final FundService funds;
    private final PersonLookupService persons;
    private final ObjectMapper mapper;
    private final AuditService audit;
    private final Clock clock;

    record Row(UUID id, UUID orgId, UUID branchId, UUID sessionId, UUID countedBy1, UUID countedBy2, String status, Timestamp confirmed1, Timestamp confirmed2) {
    }

    @Transactional(readOnly = true)
    public PageResponse<FinanceDtos.OfferingCountView> search(AccessScope scope, FinanceDtos.OfferingCountSearch req) {
        FinanceDtos.OfferingCountSearch.OfferingCountFilters f = req == null || req.filters() == null
                ? new FinanceDtos.OfferingCountSearch.OfferingCountFilters(null, null) : req.filters();
        MapSqlParameterSource ps = new MapSqlParameterSource();
        StringBuilder w = new StringBuilder(FinanceSupport.visibleByBranch(scope, ps, "o"));
        if (f.branchId() != null) {
            w.append(" and o.branch_id = :fb");
            ps.addValue("fb", f.branchId());
        }
        if (FinanceSupport.hasText(f.status())) {
            w.append(" and o.status = :fs");
            ps.addValue("fs", f.status().trim().toUpperCase());
        }
        Long total = jdbc.queryForObject("select count(*) from fin_offering_count o where " + w, ps, Long.class);
        int size = req == null || req.pagination() == null ? 20 : Math.max(1, Math.min(req.pagination().getSize(), 200));
        int page = req == null || req.pagination() == null ? 0 : Math.max(0, req.pagination().getPage());
        ps.addValue("lim", size).addValue("off", (long) page * size);
        List<UUID> ids = jdbc.query("select o.id from fin_offering_count o where " + w + " order by o.created_at desc limit :lim offset :off", ps,
                (rs, i) -> (UUID) rs.getObject(1));
        long t = total == null ? 0 : total;
        List<FinanceDtos.OfferingCountView> rows = ids.stream().map(id -> get(scope, id)).toList();
        return new PageResponse<>(rows, new PaginationResponse((int) t, (int) Math.ceil(t / (double) size), size, page));
    }

    @Transactional(readOnly = true)
    public FinanceDtos.OfferingCountView get(AccessScope scope, UUID id) {
        MapSqlParameterSource ps = new MapSqlParameterSource("id", id);
        String vis = FinanceSupport.visibleByBranch(scope, ps, "o");
        Map<String, Object> m = jdbc.query("select o.* from fin_offering_count o where o.id = :id and " + vis, ps, (rs, i) -> toMap(rs)).stream().findFirst()
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
        List<FinanceDtos.FundAmount> fundAmounts = jdbc.query("select f.fund_id, fu.name, f.amount from fin_offering_count_fund f join fin_fund fu on fu.id = f.fund_id"
                        + " where f.count_id = :id", new MapSqlParameterSource("id", id),
                (rs, i) -> new FinanceDtos.FundAmount((UUID) rs.getObject(1), rs.getString(2), rs.getBigDecimal(3).toPlainString()));
        Timestamp c1 = (Timestamp) m.get("confirmed_1_at");
        Timestamp c2 = (Timestamp) m.get("confirmed_2_at");
        return new FinanceDtos.OfferingCountView((UUID) m.get("id"), (UUID) m.get("branch_id"), support.branchName((UUID) m.get("branch_id")),
                (UUID) m.get("session_id"), (UUID) m.get("counted_by_1"), support.personName((UUID) m.get("counted_by_1")), (UUID) m.get("counted_by_2"),
                support.personName((UUID) m.get("counted_by_2")), (String) m.get("cash_breakdown"), ((BigDecimal) m.get("checks_amount")).toPlainString(),
                ((BigDecimal) m.get("total")).toPlainString(), (String) m.get("status"), c1 == null ? null : c1.toInstant(), c2 == null ? null : c2.toInstant(),
                fundAmounts, ((Timestamp) m.get("created_at")).toInstant(), (Long) m.get("version"));
    }

    private Map<String, Object> toMap(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        java.sql.ResultSetMetaData meta = rs.getMetaData();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            String col = meta.getColumnLabel(i);
            m.put(col, "cash_breakdown".equals(col) ? rs.getString(i) : rs.getObject(i));
        }
        return m;
    }

    private Row lock(AccessScope scope, UUID id) {
        get(scope, id);
        return jdbc.query("select id, organization_id, branch_id, session_id, counted_by_1, counted_by_2, status, confirmed_1_at, confirmed_2_at"
                        + " from fin_offering_count where id = :id for update", new MapSqlParameterSource("id", id),
                (rs, i) -> new Row((UUID) rs.getObject(1), (UUID) rs.getObject(2), (UUID) rs.getObject(3), (UUID) rs.getObject(4), (UUID) rs.getObject(5),
                        (UUID) rs.getObject(6), rs.getString(7), rs.getTimestamp(8), rs.getTimestamp(9))).get(0);
    }

    @Transactional
    public FinanceDtos.OfferingCountView create(AuthenticatedActor actor, AccessScope scope, FinanceDtos.OfferingCountRequest r) {
        authz.require(actor, FinanceSupport.MOD_MOVEMENTS, Action.C);
        if (r == null || r.branchId() == null || !scope.canSeeBranch(r.branchId())) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "sede");
        }
        if (r.countedBy1() == null || r.countedBy2() == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "contadores");
        }
        if (r.countedBy1().equals(r.countedBy2())) {
            throw new Exceptions("error.finance.countersDistinct", HttpStatus.UNPROCESSABLE_ENTITY);                        // [V13]
        }
        persons.getVisible(scope, r.countedBy1());
        persons.getVisible(scope, r.countedBy2());
        if (r.funds() == null || r.funds().isEmpty()) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "fondos");
        }
        BigDecimal cashTotal = sumBreakdown(r.cashBreakdownJson());
        BigDecimal checks = r.checksAmount() == null ? BigDecimal.ZERO : FinanceSupport.requiredAmount(r.checksAmount(), "cheques");
        BigDecimal total = cashTotal.add(checks);
        BigDecimal fundsSum = BigDecimal.ZERO;
        for (FinanceDtos.FundAmount fa : r.funds()) {
            if (fa.fundId() == null) {
                throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "fondo");
            }
            FundService.FundFacts fund = funds.facts(scope.organizationId(), fa.fundId());
            if (!"ACTIVE".equals(fund.status())) {
                throw new Exceptions("error.finance.fundInactive", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            if (fund.allowedCategories() != null && !fund.allowedCategories().contains("OFFERING")) {
                throw new Exceptions("error.finance.categoryNotAllowed", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            BigDecimal amount = FinanceSupport.requiredAmount(fa.amount(), "monto del fondo");
            if (amount.signum() <= 0) {
                throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "monto del fondo");
            }
            fundsSum = fundsSum.add(amount);
        }
        if (fundsSum.compareTo(total) != 0) {
            throw new Exceptions("error.finance.fundsSumMismatch", HttpStatus.UNPROCESSABLE_ENTITY, total.toPlainString());
        }
        UUID id = UUID.randomUUID();
        jdbc.update("insert into fin_offering_count (id, organization_id, branch_id, session_id, counted_by_1, counted_by_2, cash_breakdown, checks_amount,"
                        + " total, status, created_at, created_by) values (:id, :o, :b, :s, :c1, :c2, cast(:cb as jsonb), :ck, :t, 'DRAFT', :at, :by)",
                new MapSqlParameterSource("id", id).addValue("o", scope.organizationId()).addValue("b", r.branchId()).addValue("s", r.sessionId())
                        .addValue("c1", r.countedBy1()).addValue("c2", r.countedBy2()).addValue("cb", r.cashBreakdownJson()).addValue("ck", checks)
                        .addValue("t", total).addValue("at", Timestamp.from(clock.instant())).addValue("by", scope.personId()));
        for (FinanceDtos.FundAmount fa : r.funds()) {
            jdbc.update("insert into fin_offering_count_fund (count_id, fund_id, amount) values (:id, :f, :a)",
                    new MapSqlParameterSource("id", id).addValue("f", fa.fundId()).addValue("a", FinanceSupport.requiredAmount(fa.amount(), "monto")));
        }
        audit.record(new AuditService.Command(FinanceSupport.MOD_MOVEMENTS, "OFFERING_COUNT_CREATE", ENTITY, id, scope.organizationId(), r.branchId(),
                Map.of("total", total.toPlainString())));
        return get(scope, id);
    }

    /** [V13] confirma la parte de quien llama; cuando ambas partes ya confirmaron, pasa a CONFIRMED y genera los movimientos. */
    @Transactional
    public FinanceDtos.OfferingCountView confirm(AuthenticatedActor actor, AccessScope scope, UUID id) {
        authz.require(actor, FinanceSupport.MOD_MOVEMENTS, Action.S);
        Row o = lock(scope, id);
        if (!"DRAFT".equals(o.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, o.status());
        }
        UUID me = scope.personId();
        Timestamp now = Timestamp.from(clock.instant());
        if (me.equals(o.countedBy1())) {
            jdbc.update("update fin_offering_count set confirmed_1_at = coalesce(confirmed_1_at, :at) where id = :id",
                    new MapSqlParameterSource("at", now).addValue("id", id));
        } else if (me.equals(o.countedBy2())) {
            jdbc.update("update fin_offering_count set confirmed_2_at = coalesce(confirmed_2_at, :at) where id = :id",
                    new MapSqlParameterSource("at", now).addValue("id", id));
        } else {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
        Map<String, Object> both = jdbc.queryForMap("select confirmed_1_at, confirmed_2_at from fin_offering_count where id = :id", new MapSqlParameterSource("id", id));
        if (both.get("confirmed_1_at") != null && both.get("confirmed_2_at") != null) {
            generateMovements(scope, o);
            jdbc.update("update fin_offering_count set status = 'CONFIRMED', version = version + 1 where id = :id", new MapSqlParameterSource("id", id));
            audit.record(new AuditService.Command(FinanceSupport.MOD_MOVEMENTS, "OFFERING_COUNT_CONFIRMED", ENTITY, id, scope.organizationId(), o.branchId(), Map.of()));
        }
        return get(scope, id);
    }

    private void generateMovements(AccessScope scope, Row o) {
        LocalDate movementDate = o.sessionId() != null
                ? jdbc.queryForObject("select session_date from attendance_session where id = :id", new MapSqlParameterSource("id", o.sessionId()), java.sql.Date.class).toLocalDate()
                : LocalDate.now(support.zoneOf(o.branchId(), scope.organizationId()));
        List<Map.Entry<UUID, BigDecimal>> allocations = jdbc.query("select fund_id, amount from fin_offering_count_fund where count_id = :id",
                new MapSqlParameterSource("id", o.id()), (rs, i) -> Map.entry((UUID) rs.getObject(1), rs.getBigDecimal(2)));
        Timestamp now = Timestamp.from(clock.instant());
        for (Map.Entry<UUID, BigDecimal> alloc : allocations) {
            FundService.FundFacts fund = funds.facts(scope.organizationId(), alloc.getKey());
            UUID movId = UUID.randomUUID();
            jdbc.update("insert into fin_movement (id, organization_id, branch_id, movement_date, type, category, fund_id, amount, currency, method,"
                            + " anonymous, description, offering_count_id, status, submitted_by, decided_by, decided_at, created_at, created_by)"
                            + " values (:id, :o, :b, :d, 'INCOME', 'OFFERING', :f, :a, :cu, 'CASH', true, 'Conteo de ofrenda', :oc, 'APPROVED', :by, :by, :at, :at, :by)",
                    new MapSqlParameterSource("id", movId).addValue("o", scope.organizationId()).addValue("b", o.branchId()).addValue("d", java.sql.Date.valueOf(movementDate))
                            .addValue("f", alloc.getKey()).addValue("a", alloc.getValue()).addValue("cu", fund.currency()).addValue("oc", o.id())
                            .addValue("by", scope.personId()).addValue("at", now));
        }
    }

    private BigDecimal sumBreakdown(String json) {
        if (json == null || json.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            Map<String, Integer> m = mapper.readValue(json, BREAKDOWN);
            BigDecimal sum = BigDecimal.ZERO;
            for (Map.Entry<String, Integer> e : m.entrySet()) {
                sum = sum.add(new BigDecimal(e.getKey()).multiply(BigDecimal.valueOf(e.getValue() == null ? 0 : e.getValue())));
            }
            return sum;
        } catch (JsonProcessingException | NumberFormatException e) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "desglose de efectivo");
        }
    }
}
