package pe.dcs.app.features.finance.service;

import lombok.RequiredArgsConstructor;
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

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M15 · Periodo fiscal (org, año, mes): se abre automáticamente en OPEN la primera vez que un movimiento cae en ese mes
 * ({@link #ensureOpen}); cerrar (K) exige que TODOS los movimientos de ese mes estén APPROVED o REJECTED (ninguno PENDING)
 * y todas las cajas de ese mes CLOSED [V11 implícito]; reabrir (Z) exige motivo y solo ORG_ADMIN.
 * Al cerrar el periodo, los presupuestos APPROVED de ese mismo mes pasan a CLOSED (decisión propia: el spec no dice cuándo un
 * presupuesto pasa a CLOSED; cerrar el periodo contable del mes es el punto natural para congelar también su ejecución).
 */
@Service
@RequiredArgsConstructor
public class FiscalPeriodService {

    record Row(UUID id, int year, int month, String status) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final AuthorizationService authz;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Clock clock;

    @Transactional
    public Row ensureOpen(UUID orgId, LocalDate date) {
        int year = date.getYear();
        int month = date.getMonthValue();
        List<Row> r = jdbc.query("select id, year, month, status from fin_fiscal_period where organization_id = :o and year = :y and month = :m",
                new MapSqlParameterSource("o", orgId).addValue("y", year).addValue("m", month), (rs, i) -> row(rs));
        if (!r.isEmpty()) {
            return r.get(0);
        }
        UUID id = UUID.randomUUID();
        jdbc.update("insert into fin_fiscal_period (id, organization_id, year, month, status) values (:id, :o, :y, :m, 'OPEN')"
                        + " on conflict (organization_id, year, month) do nothing",
                new MapSqlParameterSource("id", id).addValue("o", orgId).addValue("y", year).addValue("m", month));
        return ensureOpen(orgId, date);
    }

    /** [V11/V5] lanza error.finance.periodClosed si el periodo de esa fecha está CLOSED; si no existe, lo abre. */
    @Transactional
    public void assertOpen(UUID orgId, LocalDate date) {
        Row r = ensureOpen(orgId, date);
        if ("CLOSED".equals(r.status())) {
            throw new Exceptions("error.finance.periodClosed", HttpStatus.UNPROCESSABLE_ENTITY, period(r.year(), r.month()));
        }
    }

    @Transactional(readOnly = true)
    public List<FinanceDtos.FiscalPeriodView> list(AccessScope scope) {
        return jdbc.query("select p.*, (select count(*) from fin_movement m where m.organization_id = p.organization_id"
                        + " and m.movement_date >= make_date(p.year, p.month, 1) and m.movement_date < make_date(p.year, p.month, 1) + interval '1 month'"
                        + " and m.status = 'PENDING') as pending"
                        + " from fin_fiscal_period p where p.organization_id = :o order by p.year desc, p.month desc",
                new MapSqlParameterSource("o", scope.organizationId()), (rs, i) -> view(rs));
    }

    @Transactional(readOnly = true)
    public FinanceDtos.FiscalPeriodView get(AccessScope scope, UUID id) {
        return jdbc.query("select p.*, (select count(*) from fin_movement m where m.organization_id = p.organization_id"
                        + " and m.movement_date >= make_date(p.year, p.month, 1) and m.movement_date < make_date(p.year, p.month, 1) + interval '1 month'"
                        + " and m.status = 'PENDING') as pending"
                        + " from fin_fiscal_period p where p.id = :id and p.organization_id = :o",
                new MapSqlParameterSource("id", id).addValue("o", scope.organizationId()), (rs, i) -> view(rs)).stream().findFirst()
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public FinanceDtos.FiscalPeriodView close(AuthenticatedActor actor, AccessScope scope, UUID id) {
        authz.require(actor, FinanceSupport.MOD_MOVEMENTS, Action.K);
        // K es la misma letra que cierra la caja (branch-level, sí la tiene ORG_BRANCH_ADMIN), pero el spec N2 dice que el
        // CIERRE DE PERIODO (org-wide, afecta todas las sedes) es de ORG_ADMIN. Se restringe aquí, igual que en reopen().
        if (scope.role() != RoleType.ORG_ADMIN) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
        Row p = lock(scope, id);
        if ("CLOSED".equals(p.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, p.status());
        }
        java.sql.Date from = java.sql.Date.valueOf(LocalDate.of(p.year(), p.month(), 1));
        java.sql.Date to = java.sql.Date.valueOf(LocalDate.of(p.year(), p.month(), 1).plusMonths(1));
        Integer pending = jdbc.queryForObject("select count(*) from fin_movement where organization_id = :o and movement_date >= :f and movement_date < :t and status = 'PENDING'",
                new MapSqlParameterSource("o", scope.organizationId()).addValue("f", from).addValue("t", to), Integer.class);
        Integer openRegisters = jdbc.queryForObject("select count(*) from fin_cash_register where organization_id = :o and register_date >= :f and register_date < :t and status = 'OPEN'",
                new MapSqlParameterSource("o", scope.organizationId()).addValue("f", from).addValue("t", to), Integer.class);
        int totalPending = (pending == null ? 0 : pending) + (openRegisters == null ? 0 : openRegisters);
        if (totalPending > 0) {
            throw new Exceptions("error.finance.periodHasPending", HttpStatus.UNPROCESSABLE_ENTITY, totalPending);          // clave propia: pendingInRegister es solo de caja
        }
        jdbc.update("update fin_fiscal_period set status = 'CLOSED', closed_at = :at, closed_by = :by where id = :id",
                new MapSqlParameterSource("at", Timestamp.from(clock.instant())).addValue("by", scope.personId()).addValue("id", id));
        jdbc.update("update fin_budget set status = 'CLOSED', updated_at = :at, updated_by = :by, version = version + 1"
                        + " where organization_id = :o and period = :d and status = 'APPROVED'",
                new MapSqlParameterSource("at", Timestamp.from(clock.instant())).addValue("by", scope.personId()).addValue("o", scope.organizationId())
                        .addValue("d", java.sql.Date.valueOf(LocalDate.of(p.year(), p.month(), 1))));
        notifications.toPersons(NotificationType.FINANCE_PERIOD_CLOSED, scope.organizationId(), notifications.orgAdmins(scope.organizationId()),
                Map.of("period", period(p.year(), p.month())), "/app/finance/periods/" + id, null);
        audit.record(new AuditService.Command(FinanceSupport.MOD_MOVEMENTS, "PERIOD_CLOSE", "FiscalPeriod", id, scope.organizationId(), null,
                Map.of("period", period(p.year(), p.month()))));
        return get(scope, id);
    }

    @Transactional
    public FinanceDtos.FiscalPeriodView reopen(AuthenticatedActor actor, AccessScope scope, UUID id, String reasonText) {
        authz.require(actor, FinanceSupport.MOD_MOVEMENTS, Action.Z);
        if (scope.role() != RoleType.ORG_ADMIN) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
        Row p = lock(scope, id);
        String reason = FinanceSupport.trim(reasonText, 500, "motivo");
        if (reason == null) {
            throw new Exceptions("error.common.reasonRequired", HttpStatus.BAD_REQUEST);
        }
        if (!"CLOSED".equals(p.status())) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, p.status());
        }
        jdbc.update("update fin_fiscal_period set status = 'OPEN', reopened_at = :at, reopened_by = :by, reopen_reason = :r where id = :id",
                new MapSqlParameterSource("at", Timestamp.from(clock.instant())).addValue("by", scope.personId()).addValue("r", reason).addValue("id", id));
        notifications.toPersons(NotificationType.FINANCE_PERIOD_REOPENED, scope.organizationId(), notifications.orgAdmins(scope.organizationId()),
                Map.of("period", period(p.year(), p.month()), "reason", reason), "/app/finance/periods/" + id, null);
        audit.record(new AuditService.Command(FinanceSupport.MOD_MOVEMENTS, "PERIOD_REOPEN", "FiscalPeriod", id, scope.organizationId(), null,
                Map.of("period", period(p.year(), p.month()), "reason", reason)));
        return get(scope, id);
    }

    private Row lock(AccessScope scope, UUID id) {
        List<Row> r = jdbc.query("select id, year, month, status from fin_fiscal_period where id = :id and organization_id = :o for update",
                new MapSqlParameterSource("id", id).addValue("o", scope.organizationId()), (rs, i) -> row(rs));
        if (r.isEmpty()) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        return r.get(0);
    }

    private static Row row(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Row((UUID) rs.getObject("id"), rs.getInt("year"), rs.getInt("month"), rs.getString("status"));
    }

    private static FinanceDtos.FiscalPeriodView view(java.sql.ResultSet rs) throws java.sql.SQLException {
        java.sql.Timestamp ca = rs.getTimestamp("closed_at");
        java.sql.Timestamp ra = rs.getTimestamp("reopened_at");
        return new FinanceDtos.FiscalPeriodView((UUID) rs.getObject("id"), rs.getInt("year"), rs.getInt("month"), rs.getString("status"),
                ca == null ? null : ca.toInstant(), (UUID) rs.getObject("closed_by"), ra == null ? null : ra.toInstant(), (UUID) rs.getObject("reopened_by"),
                rs.getString("reopen_reason"), rs.getInt("pending"));
    }

    public static String period(int year, int month) {
        return String.format("%04d-%02d", year, month);
    }
}
