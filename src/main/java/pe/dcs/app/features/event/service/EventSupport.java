package pe.dcs.app.features.event.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.util.Exceptions;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** M14 · Utilidades comunes de Eventos: alcance por sede, zona horaria, texto y el código del ticket (mismo alfabeto que M08). */
@Component
@RequiredArgsConstructor
public class EventSupport {

    public static final String MODULE = "EVENTS";
    static final Set<String> CATEGORIES = Set.of("MEMBER", "VISITOR", "GUEST", "SCHOLARSHIP", "STAFF", "TEMP_MEMBER", "TEMP_STAFF");
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    /** Organización y, si el actor no ve toda la organización, solo sus sedes visibles (los eventos de organización los ve cualquier sede). */
    public static String visibleEvent(AccessScope scope, MapSqlParameterSource ps, String a) {
        StringBuilder sb = new StringBuilder(a + ".organization_id = :org");
        ps.addValue("org", scope.organizationId());
        if (!scope.allBranches()) {
            List<UUID> ids = scope.branchIds() == null || scope.branchIds().isEmpty() ? List.of(new UUID(0, 0)) : List.copyOf(scope.branchIds());
            ps.addValue("scopeBranches", ids);
            sb.append(" and (").append(a).append(".scope = 'ORGANIZATION' or ").append(a).append(".branch_id in (:scopeBranches))");
        }
        return sb.toString();
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

    /** [V32] fecha del movimiento generado al confirmar un pago de evento — mismo criterio que {@code HrSupport.today()} (reloj del sistema, sin zona por sede). */
    public LocalDate today() {
        return LocalDate.now(clock);
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

    public void assertPersonActive(UUID orgId, UUID personId) {
        List<String> st = jdbc.queryForList("select status from person where id = :id and organization_id = :o",
                new MapSqlParameterSource("id", personId).addValue("o", orgId), String.class);
        if (st.isEmpty()) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        if (!"ACTIVE".equals(st.get(0))) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, st.get(0));
        }
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

    public static String ticketCode() {
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
