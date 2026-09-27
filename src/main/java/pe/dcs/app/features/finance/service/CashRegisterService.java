package pe.dcs.app.features.finance.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.finance.dto.FinanceDtos;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M15 · Caja diaria [V12: 1 OPEN por sede+fecha+cajero, ya UNIQUE INDEX parcial en BD]. Cerrar exige 0 movimientos PENDING
 * ligados y, si el monto contado difiere del esperado, una nota [V12].
 */
@Service
@RequiredArgsConstructor
public class CashRegisterService {

    private static final String ENTITY = "CashRegister";

    private final NamedParameterJdbcTemplate jdbc;
    private final AuthorizationService authz;
    private final AuditService audit;
    private final Clock clock;

    record Row(UUID id, UUID branchId, LocalDate date, UUID openedBy, BigDecimal opening, String status) {
    }

    @Transactional(readOnly = true)
    public PageResponse<FinanceDtos.CashRegisterView> search(AccessScope scope, FinanceDtos.CashRegisterSearch req) {
        FinanceDtos.CashRegisterSearch.CashRegisterFilters f = req == null || req.filters() == null
                ? new FinanceDtos.CashRegisterSearch.CashRegisterFilters(null, null, null, null) : req.filters();
        MapSqlParameterSource ps = new MapSqlParameterSource();
        StringBuilder w = new StringBuilder(FinanceSupport.visibleByBranch(scope, ps, "r"));
        if (f.branchId() != null) {
            w.append(" and r.branch_id = :fb");
            ps.addValue("fb", f.branchId());
        }
        if (FinanceSupport.hasText(f.status())) {
            w.append(" and r.status = :fs");
            ps.addValue("fs", f.status().trim().toUpperCase());
        }
        if (f.from() != null) {
            w.append(" and r.register_date >= :df");
            ps.addValue("df", java.sql.Date.valueOf(f.from()));
        }
        if (f.to() != null) {
            w.append(" and r.register_date <= :dt");
            ps.addValue("dt", java.sql.Date.valueOf(f.to()));
        }
        String from = " from fin_cash_register r left join branch b on b.id = r.branch_id left join person p on p.id = r.opened_by where ";
        Long total = jdbc.queryForObject("select count(*)" + from + w, ps, Long.class);
        int size = req == null || req.pagination() == null ? 20 : Math.max(1, Math.min(req.pagination().getSize(), 200));
        int page = req == null || req.pagination() == null ? 0 : Math.max(0, req.pagination().getPage());
        ps.addValue("lim", size).addValue("off", (long) page * size);
        List<FinanceDtos.CashRegisterView> rows = jdbc.query("select r.*, b.name as branch_name, trim(p.first_name || ' ' || p.last_name) as opened_by_name"
                + from + w + " order by r.register_date desc limit :lim offset :off", ps, (rs, i) -> view(rs));
        long t = total == null ? 0 : total;
        return new PageResponse<>(rows, new PaginationResponse((int) t, (int) Math.ceil(t / (double) size), size, page));
    }

    @Transactional(readOnly = true)
    public FinanceDtos.CashRegisterView get(AccessScope scope, UUID id) {
        MapSqlParameterSource ps = new MapSqlParameterSource("id", id);
        String vis = FinanceSupport.visibleByBranch(scope, ps, "r");
        return jdbc.query("select r.*, b.name as branch_name, trim(p.first_name || ' ' || p.last_name) as opened_by_name from fin_cash_register r"
                        + " left join branch b on b.id = r.branch_id left join person p on p.id = r.opened_by where r.id = :id and " + vis, ps, (rs, i) -> view(rs))
                .stream().findFirst().orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    private Row lock(AccessScope scope, UUID id) {
        get(scope, id);
        return jdbc.query("select id, branch_id, register_date, opened_by, opening_amount, status from fin_cash_register where id = :id for update",
                new MapSqlParameterSource("id", id), (rs, i) -> new Row((UUID) rs.getObject(1), (UUID) rs.getObject(2), rs.getDate(3).toLocalDate(),
                        (UUID) rs.getObject(4), rs.getBigDecimal(5), rs.getString(6))).get(0);
    }

    @Transactional
    public FinanceDtos.CashRegisterView open(AuthenticatedActor actor, AccessScope scope, FinanceDtos.CashRegisterOpenRequest r) {
        authz.require(actor, FinanceSupport.MOD_MOVEMENTS, Action.C);
        if (r == null || r.branchId() == null || !scope.canSeeBranch(r.branchId())) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "sede");
        }
        if (r.registerDate() == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "fecha");
        }
        BigDecimal opening = r.openingAmount() == null ? BigDecimal.ZERO : FinanceSupport.requiredAmount(r.openingAmount(), "monto inicial");
        if (opening.signum() < 0) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "monto inicial");
        }
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("insert into fin_cash_register (id, organization_id, branch_id, register_date, opened_by, opening_amount, status, created_at)"
                            + " values (:id, :o, :b, :d, :by, :a, 'OPEN', :at)",
                    new MapSqlParameterSource("id", id).addValue("o", scope.organizationId()).addValue("b", r.branchId())
                            .addValue("d", java.sql.Date.valueOf(r.registerDate())).addValue("by", scope.personId()).addValue("a", opening)
                            .addValue("at", Timestamp.from(clock.instant())));
        } catch (DuplicateKeyException e) {
            throw new Exceptions("error.finance.registerOpen", HttpStatus.CONFLICT);                                        // [V12]
        }
        audit.record(new AuditService.Command(FinanceSupport.MOD_MOVEMENTS, "REGISTER_OPEN", ENTITY, id, scope.organizationId(), r.branchId(), Map.of()));
        return get(scope, id);
    }

    @Transactional
    public FinanceDtos.CashRegisterView close(AuthenticatedActor actor, AccessScope scope, UUID id, FinanceDtos.CashRegisterCloseRequest r) {
        authz.require(actor, FinanceSupport.MOD_MOVEMENTS, Action.K);
        Row reg = lock(scope, id);
        if (!"OPEN".equals(reg.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, reg.status());
        }
        Integer pending = jdbc.queryForObject("select count(*) from fin_movement where cash_register_id = :id and status = 'PENDING'",
                new MapSqlParameterSource("id", id), Integer.class);
        if (pending != null && pending > 0) {
            throw new Exceptions("error.finance.pendingInRegister", HttpStatus.UNPROCESSABLE_ENTITY, pending);              // [V12]
        }
        BigDecimal income = jdbc.queryForObject("select coalesce(sum(amount), 0) from fin_movement where cash_register_id = :id and type = 'INCOME' and status = 'APPROVED'",
                new MapSqlParameterSource("id", id), BigDecimal.class);
        BigDecimal expense = jdbc.queryForObject("select coalesce(sum(amount), 0) from fin_movement where cash_register_id = :id and type = 'EXPENSE' and status = 'APPROVED'",
                new MapSqlParameterSource("id", id), BigDecimal.class);
        BigDecimal expected = reg.opening().add(income == null ? BigDecimal.ZERO : income).subtract(expense == null ? BigDecimal.ZERO : expense);
        BigDecimal counted = FinanceSupport.requiredAmount(r == null ? null : r.countedAmount(), "monto contado");
        BigDecimal difference = counted.subtract(expected);
        String notes = FinanceSupport.trim(r == null ? null : r.notes(), 500, "notas");
        if (difference.compareTo(BigDecimal.ZERO) != 0 && notes == null) {
            throw new Exceptions("error.finance.differenceNote", HttpStatus.UNPROCESSABLE_ENTITY, difference.toPlainString());   // [V12]
        }
        jdbc.update("update fin_cash_register set status = 'CLOSED', expected_amount = :e, counted_amount = :c, difference = :diff, notes = :n,"
                        + " closed_by = :by, closed_at = :at, version = version + 1 where id = :id",
                new MapSqlParameterSource("e", expected).addValue("c", counted).addValue("diff", difference).addValue("n", notes)
                        .addValue("by", scope.personId()).addValue("at", Timestamp.from(clock.instant())).addValue("id", id));
        audit.record(new AuditService.Command(FinanceSupport.MOD_MOVEMENTS, "REGISTER_CLOSE", ENTITY, id, scope.organizationId(), reg.branchId(),
                Map.of("expected", expected.toPlainString(), "counted", counted.toPlainString(), "difference", difference.toPlainString())));
        return get(scope, id);
    }

    private static FinanceDtos.CashRegisterView view(java.sql.ResultSet rs) throws java.sql.SQLException {
        java.sql.Timestamp closedAt = rs.getTimestamp("closed_at");
        java.math.BigDecimal expected = rs.getBigDecimal("expected_amount");
        java.math.BigDecimal counted = rs.getBigDecimal("counted_amount");
        java.math.BigDecimal difference = rs.getBigDecimal("difference");
        return new FinanceDtos.CashRegisterView((UUID) rs.getObject("id"), (UUID) rs.getObject("branch_id"), rs.getString("branch_name"),
                rs.getDate("register_date").toLocalDate(), (UUID) rs.getObject("opened_by"), rs.getString("opened_by_name"),
                rs.getBigDecimal("opening_amount").toPlainString(), expected == null ? null : expected.toPlainString(), counted == null ? null : counted.toPlainString(),
                difference == null ? null : difference.toPlainString(), rs.getString("notes"), rs.getString("status"), (UUID) rs.getObject("closed_by"),
                closedAt == null ? null : closedAt.toInstant(), rs.getTimestamp("created_at").toInstant(), rs.getLong("version"));
    }
}
