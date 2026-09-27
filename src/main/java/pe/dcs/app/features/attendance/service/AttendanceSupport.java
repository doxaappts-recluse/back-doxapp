package pe.dcs.app.features.attendance.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.util.Exceptions;

import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/** Utilidades comunes de M09 (sedes, zonas horarias, catálogo, nombres). */
@Component
@RequiredArgsConstructor
class AttendanceSupport {

    private final NamedParameterJdbcTemplate jdbc;

    /** Nombre de la sede si existe, es de la organización, está en el alcance y está activa. */
    String activeBranchName(AccessScope scope, UUID branchId) {
        List<String[]> rows = jdbc.query("select name, status from branch where id = :id and organization_id = :o",
                new MapSqlParameterSource("id", branchId).addValue("o", scope.organizationId()), (rs, i) -> new String[]{rs.getString(1), rs.getString(2)});
        if (rows.isEmpty() || !scope.canSeeBranch(branchId)) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        if (!"ACTIVE".equals(rows.get(0)[1])) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, rows.get(0)[1]);
        }
        return rows.get(0)[0];
    }

    /** Zona horaria de la sede o, si no tiene, la de la organización. */
    ZoneId zoneOf(UUID branchId) {
        List<String> z = jdbc.queryForList("select coalesce(b.timezone, o.timezone) from branch b join organization o on o.id = b.organization_id where b.id = :b",
                new MapSqlParameterSource("b", branchId), String.class);
        try {
            return z.isEmpty() || z.get(0) == null ? ZoneId.of("America/Lima") : ZoneId.of(z.get(0));
        } catch (RuntimeException e) {
            return ZoneId.of("America/Lima");
        }
    }

    /** Código de catálogo activo (base u organización); null si no viene; 400 si no existe. */
    String catalogCode(UUID orgId, String type, String raw, String label) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String code = raw.trim().toUpperCase();
        Integer n = jdbc.queryForObject("select count(*) from catalog_item where type = :t and code = :c and active and (organization_id is null or organization_id = :o)",
                new MapSqlParameterSource("t", type).addValue("c", code).addValue("o", orgId), Integer.class);
        if (n == null || n == 0) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, label);
        }
        return code;
    }

    String personName(UUID personId) {
        if (personId == null) {
            return null;
        }
        List<String> n = jdbc.queryForList("select trim(first_name || ' ' || last_name) from person where id = :id", new MapSqlParameterSource("id", personId), String.class);
        return n.isEmpty() ? "" : n.get(0);
    }

    String branchName(UUID branchId) {
        List<String> n = jdbc.queryForList("select name from branch where id = :id", new MapSqlParameterSource("id", branchId), String.class);
        return n.isEmpty() ? "" : n.get(0);
    }

    /** Condición SQL de alcance por sede sobre la columna {@code a.branch_id}. */
    static String branchScope(AccessScope scope, MapSqlParameterSource ps, String a) {
        StringBuilder sb = new StringBuilder(a + ".organization_id = :org");
        ps.addValue("org", scope.organizationId());
        if (!scope.allBranches()) {
            List<UUID> ids = scope.branchIds() == null || scope.branchIds().isEmpty() ? List.of(new UUID(0, 0)) : List.copyOf(scope.branchIds());
            ps.addValue("scopeBranches", ids);
            sb.append(" and ").append(a).append(".branch_id in (:scopeBranches)");
        }
        return sb.toString();
    }

    static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
