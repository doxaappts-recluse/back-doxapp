package pe.dcs.app.features.finance.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.finance.dto.FinanceDtos;
import pe.dcs.app.features.notification.domain.NotificationType;
import pe.dcs.app.features.notification.service.NotificationService;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.AuthorizationService;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.RoleType;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PaginationResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * M15 · Presupuestos (FIN_BUDGETS): DRAFT → APPROVED [A] (bloquea edición) → CLOSED (al cerrar el periodo fiscal del mes, ver
 * {@link FiscalPeriodService}). [V2] único por alcance/sede/fondo/categoría/periodo (ya UNIQUE en BD). [V17] ejecución con
 * alertas 80 %/100 % ({@code msg.finance.overspend}, dedupe por umbral) y bloqueo opcional ({@code fin_rules.overspend_block}).
 */
@Service
@RequiredArgsConstructor
public class BudgetService {

    private static final Set<String> CATEGORIES = union();
    private static final String ENTITY = "Budget";
    private static final DateTimeFormatter PERIOD_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final NamedParameterJdbcTemplate jdbc;
    private final AuthorizationService authz;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Clock clock;

    private static Set<String> union() {
        Set<String> s = new java.util.HashSet<>(FinanceSupport.INCOME_CATEGORIES);
        s.addAll(FinanceSupport.EXPENSE_CATEGORIES);
        return s;
    }

    record Row(UUID id, String scope, UUID branchId, UUID fundId, String category, LocalDate period, BigDecimal amount, String status) {
    }

    // ---------------------------------------------------------------- consulta

    @Transactional(readOnly = true)
    public PageResponse<FinanceDtos.BudgetView> search(AccessScope scope, FinanceDtos.BudgetSearch req) {
        FinanceDtos.BudgetSearch.BudgetFilters f = req == null || req.filters() == null
                ? new FinanceDtos.BudgetSearch.BudgetFilters(null, null, null, null, null) : req.filters();
        MapSqlParameterSource ps = new MapSqlParameterSource();
        StringBuilder w = new StringBuilder(FinanceSupport.visibleBudget(scope, ps, "u"));
        if (f.branchId() != null) {
            w.append(" and u.branch_id = :fb");
            ps.addValue("fb", f.branchId());
        }
        if (f.fundId() != null) {
            w.append(" and u.fund_id = :ff");
            ps.addValue("ff", f.fundId());
        }
        if (FinanceSupport.hasText(f.category())) {
            w.append(" and u.category = :fc");
            ps.addValue("fc", f.category().trim().toUpperCase());
        }
        if (FinanceSupport.hasText(f.status())) {
            w.append(" and u.status = :fs");
            ps.addValue("fs", f.status().trim().toUpperCase());
        }
        if (FinanceSupport.hasText(f.period())) {
            w.append(" and u.period = :fp");
            ps.addValue("fp", Date.valueOf(parsePeriod(f.period())));
        }
        String from = " from fin_budget u left join branch b on b.id = u.branch_id join fin_fund fu on fu.id = u.fund_id where ";
        Long total = jdbc.queryForObject("select count(*)" + from + w, ps, Long.class);
        int size = req == null || req.pagination() == null ? 20 : Math.max(1, Math.min(req.pagination().getSize(), 200));
        int page = req == null || req.pagination() == null ? 0 : Math.max(0, req.pagination().getPage());
        ps.addValue("lim", size).addValue("off", (long) page * size);
        List<FinanceDtos.BudgetView> rows = jdbc.query("select u.*, b.name as branch_name, fu.name as fund_name" + from + w
                + " order by u.period desc, fu.name limit :lim offset :off", ps, (rs, i) -> view(rs));
        long t = total == null ? 0 : total;
        return new PageResponse<>(rows, new PaginationResponse((int) t, (int) Math.ceil(t / (double) size), size, page));
    }

    @Transactional(readOnly = true)
    public FinanceDtos.BudgetView get(AccessScope scope, UUID id) {
        MapSqlParameterSource ps = new MapSqlParameterSource("id", id);
        String vis = FinanceSupport.visibleBudget(scope, ps, "u");
        return jdbc.query("select u.*, b.name as branch_name, fu.name as fund_name from fin_budget u left join branch b on b.id = u.branch_id"
                        + " join fin_fund fu on fu.id = u.fund_id where u.id = :id and " + vis, ps, (rs, i) -> view(rs)).stream().findFirst()
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    private Row lock(AccessScope scope, UUID id) {
        get(scope, id);
        List<Row> r = jdbc.query("select id, scope, branch_id, fund_id, category, period, amount, status from fin_budget where id = :id for update",
                new MapSqlParameterSource("id", id), (rs, i) -> row(rs));
        return r.get(0);
    }

    // ---------------------------------------------------------------- alta y edición

    @Transactional
    public FinanceDtos.BudgetView create(AuthenticatedActor actor, AccessScope scope, FinanceDtos.BudgetRequest r) {
        authz.require(actor, FinanceSupport.MOD_BUDGETS, Action.C);
        String budgetScope = validateScope(scope, r);
        String category = validateCategory(r.category());
        LocalDate period = parsePeriod(r.period());
        BigDecimal amount = FinanceSupport.requiredAmount(r.amount(), "monto");
        if (amount.signum() <= 0) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "monto");
        }
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("insert into fin_budget (id, organization_id, scope, branch_id, fund_id, category, period, amount, status, created_at, created_by)"
                            + " values (:id, :o, :sc, :b, :f, :c, :p, :a, 'DRAFT', :at, :by)",
                    new MapSqlParameterSource("id", id).addValue("o", scope.organizationId()).addValue("sc", budgetScope)
                            .addValue("b", "BRANCH".equals(budgetScope) ? r.branchId() : null).addValue("f", r.fundId()).addValue("c", category)
                            .addValue("p", Date.valueOf(period)).addValue("a", amount).addValue("at", Timestamp.from(clock.instant())).addValue("by", scope.personId()));
        } catch (DuplicateKeyException e) {
            throw new Exceptions("error.finance.budgetTaken", HttpStatus.CONFLICT);                                        // [V2]
        }
        audit.record(new AuditService.Command(FinanceSupport.MOD_BUDGETS, "CREATE", ENTITY, id, scope.organizationId(),
                "BRANCH".equals(budgetScope) ? r.branchId() : null, Map.of("fundId", r.fundId().toString(), "category", category)));
        return get(scope, id);
    }

    @Transactional
    public FinanceDtos.BudgetView update(AuthenticatedActor actor, AccessScope scope, UUID id, FinanceDtos.BudgetRequest r) {
        authz.require(actor, FinanceSupport.MOD_BUDGETS, Action.E);
        Row b = lock(scope, id);
        if (!"DRAFT".equals(b.status())) {
            throw new Exceptions("error.finance.budgetLocked", HttpStatus.CONFLICT);
        }
        BigDecimal amount = FinanceSupport.requiredAmount(r.amount(), "monto");
        if (amount.signum() <= 0) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "monto");
        }
        int n = jdbc.update("update fin_budget set amount = :a, updated_at = :at, updated_by = :by, version = version + 1"
                        + " where id = :id and (cast(:v as bigint) is null or version = :v)",
                new MapSqlParameterSource("a", amount).addValue("at", Timestamp.from(clock.instant())).addValue("by", scope.personId())
                        .addValue("id", id).addValue("v", r.version()));
        if (n == 0) {
            throw new Exceptions("error.common.concurrentUpdate", HttpStatus.CONFLICT);
        }
        audit.record(new AuditService.Command(FinanceSupport.MOD_BUDGETS, "UPDATE", ENTITY, id, scope.organizationId(), b.branchId(), Map.of()));
        return get(scope, id);
    }

    @Transactional
    public FinanceDtos.BudgetView approve(AuthenticatedActor actor, AccessScope scope, UUID id) {
        authz.require(actor, FinanceSupport.MOD_BUDGETS, Action.A);
        Row b = lock(scope, id);
        // Mismo tope que create(): un presupuesto de alcance ORG (toda la organización) solo lo aprueba ORG_ADMIN.
        // FIN_BUDGETS no tiene entrada en AuthorizationService.BRANCH_ADMIN_CAPS (el set de acciones N3 del spec ya
        // coincide con el declarado), así que sin este chequeo un ORG_BRANCH_ADMIN con acción A podría aprobar
        // presupuestos de otras sedes / de toda la organización — bug encontrado en revisión, corregido aquí.
        if ("ORG".equals(b.scope()) && scope.role() != RoleType.ORG_ADMIN) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
        if (!"DRAFT".equals(b.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, b.status());
        }
        jdbc.update("update fin_budget set status = 'APPROVED', updated_at = :at, updated_by = :by, version = version + 1 where id = :id",
                new MapSqlParameterSource("at", Timestamp.from(clock.instant())).addValue("by", scope.personId()).addValue("id", id));
        audit.record(new AuditService.Command(FinanceSupport.MOD_BUDGETS, "APPROVE", ENTITY, id, scope.organizationId(), b.branchId(), Map.of()));
        return get(scope, id);
    }

    private String validateScope(AccessScope scope, FinanceDtos.BudgetRequest r) {
        String budgetScope = r.scope() == null ? null : r.scope().trim().toUpperCase();
        if (!Set.of("ORG", "BRANCH").contains(budgetScope)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "alcance");
        }
        if ("ORG".equals(budgetScope) && scope.role() != RoleType.ORG_ADMIN) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);                                          // solo ORG_ADMIN crea presupuestos de organización
        }
        if ("BRANCH".equals(budgetScope) && (r.branchId() == null || !scope.canSeeBranch(r.branchId()))) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "sede");
        }
        if (r.fundId() == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "fondo");
        }
        return budgetScope;
    }

    private static String validateCategory(String category) {
        String c = category == null ? null : category.trim().toUpperCase();
        if (c == null || !CATEGORIES.contains(c)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "categoría");
        }
        return c;
    }

    static LocalDate parsePeriod(String period) {
        if (period == null || period.isBlank()) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "periodo");
        }
        try {
            return java.time.YearMonth.parse(period.trim(), PERIOD_FMT).atDay(1);
        } catch (DateTimeParseException e) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "periodo");
        }
    }

    // ---------------------------------------------------------------- ejecución [V17]

    /**
     * Gasto acumulado APPROVED de ese fondo+categoría+periodo dentro del alcance del presupuesto (toda la organización si
     * scope=ORG, solo la sede si scope=BRANCH).
     */
    private BigDecimal spent(UUID orgId, String budgetScope, UUID branchId, UUID fundId, String category, LocalDate period) {
        MapSqlParameterSource ps = new MapSqlParameterSource("o", orgId).addValue("f", fundId).addValue("c", category)
                .addValue("from", Date.valueOf(period)).addValue("to", Date.valueOf(period.plusMonths(1)));
        String branchFilter = "BRANCH".equals(budgetScope) ? " and branch_id = :b" : "";
        if ("BRANCH".equals(budgetScope)) {
            ps.addValue("b", branchId);
        }
        BigDecimal s = jdbc.queryForObject("select coalesce(sum(amount), 0) from fin_movement where organization_id = :o and fund_id = :f and category = :c"
                        + " and type = 'EXPENSE' and status = 'APPROVED' and movement_date >= :from and movement_date < :to" + branchFilter, ps, BigDecimal.class);
        return s == null ? BigDecimal.ZERO : s;
    }

    /**
     * Se llama AL REGISTRAR un movimiento EXPENSE (no al aprobarlo: así lo pide la especificación de la tarea). Revisa todo
     * presupuesto APPROVED que matchee fondo+categoría+periodo en el alcance del movimiento (ORG y/o BRANCH de esa sede) y:
     * bloquea la creación (422 overspendBlocked) si {@code fin_rules.overspend_block} y el acumulado + este monto superaría el
     * 100 %; si no bloquea, avisa una vez por umbral (80 %, 100 %) con dedupeKey "budget:{budgetId}:{umbral}".
     */
    @Transactional
    public void onExpenseRegistered(AccessScope scope, UUID branchId, UUID fundId, String category, LocalDate movementDate, BigDecimal amount, boolean overspendBlock) {
        LocalDate period = movementDate.withDayOfMonth(1);
        List<Row> budgets = jdbc.query("select id, scope, branch_id, fund_id, category, period, amount, status from fin_budget"
                        + " where organization_id = :o and fund_id = :f and category = :c and period = :p and status = 'APPROVED'"
                        + " and (scope = 'ORG' or (scope = 'BRANCH' and branch_id = :b))",
                new MapSqlParameterSource("o", scope.organizationId()).addValue("f", fundId).addValue("c", category).addValue("p", Date.valueOf(period))
                        .addValue("b", branchId), (rs, i) -> row(rs));
        if (budgets.isEmpty()) {
            return;
        }
        // primera pasada: si alguno se excedería y overspendBlock está activo, se bloquea TODO antes de avisar nada.
        for (Row b : budgets) {
            BigDecimal projected = spent(scope.organizationId(), b.scope(), b.branchId(), fundId, category, period).add(amount);
            if (overspendBlock && projected.compareTo(b.amount()) > 0) {
                throw new Exceptions("error.finance.overspendBlocked", HttpStatus.UNPROCESSABLE_ENTITY);
            }
        }
        // segunda pasada: avisos 80 %/100 %, una vez por umbral y presupuesto.
        for (Row b : budgets) {
            BigDecimal projected = spent(scope.organizationId(), b.scope(), b.branchId(), fundId, category, period).add(amount);
            int pct = projected.multiply(BigDecimal.valueOf(100)).divide(b.amount(), 0, RoundingMode.FLOOR).intValue();
            int threshold = pct >= 100 ? 100 : (pct >= 80 ? 80 : 0);
            if (threshold == 0) {
                continue;
            }
            List<UUID> targets = "BRANCH".equals(b.scope()) ? notifications.branchAdmins(scope.organizationId(), b.branchId()) : notifications.orgAdmins(scope.organizationId());
            notifications.toPersons(NotificationType.FINANCE_BUDGET_OVERSPEND, scope.organizationId(), targets, Map.of("pct", String.valueOf(threshold)),
                    "/app/finance/budgets/" + b.id(), "budget:" + b.id() + ":" + threshold);
        }
    }

    private static Row row(java.sql.ResultSet rs) throws java.sql.SQLException {
        Date period = rs.getDate("period");
        return new Row((UUID) rs.getObject("id"), rs.getString("scope"), (UUID) rs.getObject("branch_id"), (UUID) rs.getObject("fund_id"),
                rs.getString("category"), period == null ? null : period.toLocalDate(), rs.getBigDecimal("amount"), rs.getString("status"));
    }

    /** Incluye la ejecución (gastado + % ejecutado) en la propia respuesta: no hay endpoint /execution aparte. */
    private FinanceDtos.BudgetView view(java.sql.ResultSet rs) throws java.sql.SQLException {
        LocalDate period = rs.getDate("period").toLocalDate();
        BigDecimal amount = rs.getBigDecimal("amount");
        UUID id = (UUID) rs.getObject("id");
        String budgetScope = rs.getString("scope");
        UUID branchId = (UUID) rs.getObject("branch_id");
        UUID fundId = (UUID) rs.getObject("fund_id");
        String category = rs.getString("category");
        BigDecimal spentAmt = spent((UUID) rs.getObject("organization_id"), budgetScope, branchId, fundId, category, period);
        String executedPct = amount.signum() == 0 ? "0" : spentAmt.multiply(BigDecimal.valueOf(100)).divide(amount, 2, RoundingMode.HALF_UP).toPlainString();
        return new FinanceDtos.BudgetView(id, budgetScope, branchId, rs.getString("branch_name"), fundId, rs.getString("fund_name"), category,
                period.format(PERIOD_FMT), amount.toPlainString(), rs.getString("status"), spentAmt.toPlainString(), executedPct,
                rs.getTimestamp("created_at").toInstant(), rs.getLong("version"));
    }
}
