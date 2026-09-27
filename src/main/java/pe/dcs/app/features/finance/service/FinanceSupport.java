package pe.dcs.app.features.finance.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.util.Exceptions;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** M15 · Utilidades comunes de Finanzas: alcance por sede, zona horaria y montos. Mismo patrón que {@code EventSupport} (M14). */
@Component
@RequiredArgsConstructor
public class FinanceSupport {

    public static final String MOD_MOVEMENTS = "FIN_MOVEMENTS";
    public static final String MOD_FUNDS = "FIN_FUNDS";
    public static final String MOD_BUDGETS = "FIN_BUDGETS";
    public static final String MOD_DONORS = "FIN_DONORS";

    public static final Set<String> INCOME_CATEGORIES = Set.of("TITHE", "OFFERING", "DONATION", "OTHER_INCOME", "SERVICE_FEE");
    public static final Set<String> EXPENSE_CATEGORIES = Set.of("EXPENSE", "PAYROLL", "MAINTENANCE", "UTILITIES", "SUPPLIES", "OTHER_EXPENSE");

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    /** Tablas con branch_id NOT NULL (fin_movement, fin_cash_register, fin_offering_count): solo sedes visibles. */
    public static String visibleByBranch(AccessScope scope, MapSqlParameterSource ps, String alias) {
        StringBuilder sb = new StringBuilder(alias + ".organization_id = :org");
        ps.addValue("org", scope.organizationId());
        if (!scope.allBranches()) {
            ps.addValue("scopeBranches", branchIdsOrNone(scope));
            sb.append(" and ").append(alias).append(".branch_id in (:scopeBranches)");
        }
        return sb.toString();
    }

    /** Tablas con branch_id NULLABLE = de toda la organización (fin_account): la organización o su sede visible. */
    public static String visibleOrgOrBranch(AccessScope scope, MapSqlParameterSource ps, String alias) {
        StringBuilder sb = new StringBuilder(alias + ".organization_id = :org");
        ps.addValue("org", scope.organizationId());
        if (!scope.allBranches()) {
            ps.addValue("scopeBranches", branchIdsOrNone(scope));
            sb.append(" and (").append(alias).append(".branch_id is null or ").append(alias).append(".branch_id in (:scopeBranches))");
        }
        return sb.toString();
    }

    /** fin_budget: alcance ORG (visible a toda la organización) o BRANCH de una sede visible. */
    public static String visibleBudget(AccessScope scope, MapSqlParameterSource ps, String alias) {
        StringBuilder sb = new StringBuilder(alias + ".organization_id = :org");
        ps.addValue("org", scope.organizationId());
        if (!scope.allBranches()) {
            ps.addValue("scopeBranches", branchIdsOrNone(scope));
            sb.append(" and (").append(alias).append(".scope = 'ORG' or ").append(alias).append(".branch_id in (:scopeBranches))");
        }
        return sb.toString();
    }

    private static List<UUID> branchIdsOrNone(AccessScope scope) {
        return scope.branchIds() == null || scope.branchIds().isEmpty() ? List.of(new UUID(0, 0)) : List.copyOf(scope.branchIds());
    }

    public ZoneId zoneOf(UUID branchId, UUID orgId) {
        List<String> z = branchId != null
                ? jdbc.queryForList("select coalesce(b.timezone, o.timezone) from branch b join organization o on o.id = b.organization_id where b.id = :b",
                        new MapSqlParameterSource("b", branchId), String.class)
                : jdbc.queryForList("select timezone from organization where id = :o", new MapSqlParameterSource("o", orgId), String.class);
        try {
            return z.isEmpty() || z.get(0) == null ? ZoneId.of("America/Lima") : ZoneId.of(z.get(0));
        } catch (RuntimeException e) {
            return ZoneId.of("America/Lima");
        }
    }

    public java.time.Instant now() {
        return clock.instant();
    }

    public String branchName(UUID branchId) {
        if (branchId == null) {
            return null;
        }
        List<String> n = jdbc.queryForList("select name from branch where id = :id", new MapSqlParameterSource("id", branchId), String.class);
        return n.isEmpty() ? "" : n.get(0);
    }

    public String personName(UUID personId) {
        if (personId == null) {
            return null;
        }
        List<String> n = jdbc.queryForList("select trim(first_name || ' ' || last_name) from person where id = :id", new MapSqlParameterSource("id", personId), String.class);
        return n.isEmpty() ? "" : n.get(0);
    }

    public static String trim(String s, int max, String label) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        if (t.isEmpty()) {
            return null;
        }
        if (t.length() > max) {
            throw new Exceptions("error.common.tooLong", HttpStatus.BAD_REQUEST, label, max);
        }
        return t;
    }

    public static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    /** Monto obligatorio: acepta "1234.56", nunca notación exponencial ni coma decimal. */
    public static BigDecimal requiredAmount(String s, String label) {
        if (s == null || s.isBlank()) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, label);
        }
        try {
            return new BigDecimal(s.trim());
        } catch (NumberFormatException e) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, label);
        }
    }

    /** Monto opcional, null si el texto viene vacío. */
    public static BigDecimal optionalAmount(String s, String label) {
        return hasText(s) ? requiredAmount(s, label) : null;
    }
}
