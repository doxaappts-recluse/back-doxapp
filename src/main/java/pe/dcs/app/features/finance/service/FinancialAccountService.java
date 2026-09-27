package pe.dcs.app.features.finance.service;

import lombok.RequiredArgsConstructor;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** M15 · Cuentas financieras (FIN_MOVEMENTS): CRUD simple; branch_id null = cuenta de toda la organización. */
@Service
@RequiredArgsConstructor
public class FinancialAccountService {

    private static final Set<String> TYPES = Set.of("CASH", "BANK", "WALLET");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "INACTIVE");
    private static final String ENTITY = "FinancialAccount";

    private final NamedParameterJdbcTemplate jdbc;
    private final AuthorizationService authz;
    private final AuditService audit;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<FinanceDtos.AccountView> search(AccessScope scope, FinanceDtos.AccountSearch req) {
        FinanceDtos.AccountSearch.AccountFilters f = req == null || req.filters() == null
                ? new FinanceDtos.AccountSearch.AccountFilters(null, null, null) : req.filters();
        MapSqlParameterSource ps = new MapSqlParameterSource();
        StringBuilder w = new StringBuilder(FinanceSupport.visibleOrgOrBranch(scope, ps, "a"));
        if (f.branchId() != null) {
            w.append(" and a.branch_id = :fb");
            ps.addValue("fb", f.branchId());
        }
        if (FinanceSupport.hasText(f.status())) {
            w.append(" and a.status = :fs");
            ps.addValue("fs", f.status().trim().toUpperCase());
        }
        if (FinanceSupport.hasText(f.type())) {
            w.append(" and a.type = :ft");
            ps.addValue("ft", f.type().trim().toUpperCase());
        }
        String from = " from fin_account a left join branch b on b.id = a.branch_id where ";
        Long total = jdbc.queryForObject("select count(*)" + from + w, ps, Long.class);
        int size = req == null || req.pagination() == null ? 20 : Math.max(1, Math.min(req.pagination().getSize(), 200));
        int page = req == null || req.pagination() == null ? 0 : Math.max(0, req.pagination().getPage());
        ps.addValue("lim", size).addValue("off", (long) page * size);
        List<FinanceDtos.AccountView> rows = jdbc.query("select a.*, b.name as branch_name" + from + w + " order by a.name limit :lim offset :off",
                ps, (rs, i) -> view(rs));
        long t = total == null ? 0 : total;
        return new PageResponse<>(rows, new PaginationResponse((int) t, (int) Math.ceil(t / (double) size), size, page));
    }

    @Transactional(readOnly = true)
    public FinanceDtos.AccountView get(AccessScope scope, UUID id) {
        MapSqlParameterSource ps = new MapSqlParameterSource("id", id);
        String vis = FinanceSupport.visibleOrgOrBranch(scope, ps, "a");
        return jdbc.query("select a.*, b.name as branch_name from fin_account a left join branch b on b.id = a.branch_id where a.id = :id and " + vis,
                ps, (rs, i) -> view(rs)).stream().findFirst().orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    /** Cuenta ACTIVE, visible al alcance (para validar accountId en un movimiento). */
    @Transactional(readOnly = true)
    public void assertActiveVisible(AccessScope scope, UUID id) {
        FinanceDtos.AccountView v = get(scope, id);
        if (!"ACTIVE".equals(v.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.UNPROCESSABLE_ENTITY, v.status());
        }
    }

    private void lock(AccessScope scope, UUID id) {
        get(scope, id);
        jdbc.queryForObject("select 1 from fin_account where id = :id for update", new MapSqlParameterSource("id", id), Integer.class);
    }

    @Transactional
    public FinanceDtos.AccountView create(AuthenticatedActor actor, AccessScope scope, FinanceDtos.AccountRequest r) {
        authz.require(actor, FinanceSupport.MOD_MOVEMENTS, Action.C);
        String name = FinanceSupport.trim(r == null ? null : r.name(), 120, "nombre");
        if (name == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "nombre");
        }
        String type = r.type() == null ? null : r.type().trim().toUpperCase();
        if (type == null || !TYPES.contains(type)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "tipo");
        }
        if (r.branchId() != null && !scope.canSeeBranch(r.branchId())) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
        BigDecimal opening = r.openingBalance() == null ? BigDecimal.ZERO : FinanceSupport.requiredAmount(r.openingBalance(), "saldo inicial");
        if (opening.signum() < 0) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "saldo inicial");
        }
        String currency = FinanceSupport.hasText(r.currency()) ? r.currency().trim().toUpperCase() : "PEN";
        UUID id = UUID.randomUUID();
        jdbc.update("insert into fin_account (id, organization_id, branch_id, name, type, currency, opening_balance, status, created_at, created_by)"
                        + " values (:id, :o, :b, :n, :ty, :cu, :ob, 'ACTIVE', :at, :by)",
                new MapSqlParameterSource("id", id).addValue("o", scope.organizationId()).addValue("b", r.branchId()).addValue("n", name)
                        .addValue("ty", type).addValue("cu", currency).addValue("ob", opening).addValue("at", Timestamp.from(clock.instant()))
                        .addValue("by", scope.personId()));
        audit.record(new AuditService.Command(FinanceSupport.MOD_MOVEMENTS, "ACCOUNT_CREATE", ENTITY, id, scope.organizationId(), r.branchId(), Map.of("name", name)));
        return get(scope, id);
    }

    @Transactional
    public FinanceDtos.AccountView update(AuthenticatedActor actor, AccessScope scope, UUID id, FinanceDtos.AccountRequest r) {
        authz.require(actor, FinanceSupport.MOD_MOVEMENTS, Action.E);
        lock(scope, id);
        String name = FinanceSupport.trim(r.name(), 120, "nombre");
        if (name == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "nombre");
        }
        String type = r.type() == null ? null : r.type().trim().toUpperCase();
        if (type == null || !TYPES.contains(type)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "tipo");
        }
        String currency = FinanceSupport.hasText(r.currency()) ? r.currency().trim().toUpperCase() : "PEN";
        int n = jdbc.update("update fin_account set name = :n, type = :ty, currency = :cu, updated_at = :at, updated_by = :by, version = version + 1"
                        + " where id = :id and (cast(:v as bigint) is null or version = :v)",
                new MapSqlParameterSource("n", name).addValue("ty", type).addValue("cu", currency).addValue("at", Timestamp.from(clock.instant()))
                        .addValue("by", scope.personId()).addValue("id", id).addValue("v", r.version()));
        if (n == 0) {
            throw new Exceptions("error.common.concurrentUpdate", HttpStatus.CONFLICT);
        }
        audit.record(new AuditService.Command(FinanceSupport.MOD_MOVEMENTS, "ACCOUNT_UPDATE", ENTITY, id, scope.organizationId(), null, Map.of()));
        return get(scope, id);
    }

    @Transactional
    public FinanceDtos.AccountView setStatus(AuthenticatedActor actor, AccessScope scope, UUID id, String statusRaw) {
        authz.require(actor, FinanceSupport.MOD_MOVEMENTS, Action.E);
        lock(scope, id);
        String status = statusRaw == null ? null : statusRaw.trim().toUpperCase();
        if (status == null || !STATUSES.contains(status)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "estado");
        }
        jdbc.update("update fin_account set status = :s, updated_at = :at, updated_by = :by, version = version + 1 where id = :id",
                new MapSqlParameterSource("s", status).addValue("at", Timestamp.from(clock.instant())).addValue("by", scope.personId()).addValue("id", id));
        audit.record(new AuditService.Command(FinanceSupport.MOD_MOVEMENTS, "ACCOUNT_STATUS", ENTITY, id, scope.organizationId(), null, Map.of("status", status)));
        return get(scope, id);
    }

    private static FinanceDtos.AccountView view(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new FinanceDtos.AccountView((UUID) rs.getObject("id"), rs.getString("name"), rs.getString("type"), (UUID) rs.getObject("branch_id"),
                rs.getString("branch_name"), rs.getString("currency"), rs.getBigDecimal("opening_balance").toPlainString(), rs.getString("status"),
                rs.getTimestamp("created_at").toInstant(), rs.getLong("version"));
    }
}
