package pe.dcs.app.features.facility.service;

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
import java.util.UUID;

/** M16 · Utilidades comunes de Espacios (SPACES) e Inventario (INVENTORY): alcance por sede, zona horaria y montos. Mismo patrón que {@code FinanceSupport} (M15). */
@Component
@RequiredArgsConstructor
public class FacilitySupport {

    public static final String MOD_SPACES = "SPACES";
    public static final String MOD_INVENTORY = "INVENTORY";

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    /** Tablas con branch_id NOT NULL (space, inventory_item, inventory_count): solo sedes visibles. */
    public static String visibleByBranch(AccessScope scope, MapSqlParameterSource ps, String alias) {
        StringBuilder sb = new StringBuilder(alias + ".organization_id = :org");
        ps.addValue("org", scope.organizationId());
        if (!scope.allBranches()) {
            ps.addValue("scopeBranches", branchIdsOrNone(scope));
            sb.append(" and ").append(alias).append(".branch_id in (:scopeBranches)");
        }
        return sb.toString();
    }

    public static List<UUID> branchIdsOrNone(AccessScope scope) {
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

    public boolean branchActive(UUID orgId, UUID branchId) {
        List<String> s = jdbc.queryForList("select status from branch where id = :id and organization_id = :o", new MapSqlParameterSource("id", branchId).addValue("o", orgId), String.class);
        return !s.isEmpty() && "ACTIVE".equals(s.get(0));
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

    public static BigDecimal optionalAmount(String s, String label) {
        return hasText(s) ? requiredAmount(s, label) : null;
    }
}
