package pe.dcs.app.features.training.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.RoleType;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/** M13 · Utilidades comunes: alcance por sede (OWN para el docente cuando el actor es ORG_USER), zona horaria, nombres y texto. */
@Component
@RequiredArgsConstructor
public class TrainingSupport {

    public static final String MODULE = "TRAINING";
    static final int ADULT_AGE = 18;

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    /** Organización, sedes visibles y, para ORG_USER, solo los dictados donde es el docente (OWN). */
    public static String visibleClass(AccessScope scope, MapSqlParameterSource ps, String a) {
        StringBuilder sb = new StringBuilder(a + ".organization_id = :org");
        ps.addValue("org", scope.organizationId());
        if (!scope.allBranches()) {
            List<UUID> ids = scope.branchIds() == null || scope.branchIds().isEmpty() ? List.of(new UUID(0, 0)) : List.copyOf(scope.branchIds());
            ps.addValue("scopeBranches", ids);
            sb.append(" and ").append(a).append(".branch_id in (:scopeBranches)");
        }
        if (scope.role() == RoleType.ORG_USER) {
            UUID me = scope.personId() == null ? new UUID(0, 0) : scope.personId();
            ps.addValue("meOwn", me);
            sb.append(" and ").append(a).append(".teacher_person_id = :meOwn");
        }
        return sb.toString();
    }

    public ZoneId zoneOf(UUID branchId) {
        List<String> z = jdbc.queryForList("select coalesce(b.timezone, o.timezone) from branch b join organization o on o.id = b.organization_id where b.id = :b",
                new MapSqlParameterSource("b", branchId), String.class);
        try {
            return z.isEmpty() || z.get(0) == null ? ZoneId.of("America/Lima") : ZoneId.of(z.get(0));
        } catch (RuntimeException e) {
            return ZoneId.of("America/Lima");
        }
    }

    public LocalDate today(UUID branchId) {
        return LocalDate.now(clock.withZone(zoneOf(branchId)));
    }

    public String branchName(UUID branchId) {
        List<String> n = jdbc.queryForList("select name from branch where id = :id", new MapSqlParameterSource("id", branchId), String.class);
        return n.isEmpty() ? "" : n.get(0);
    }

    public void assertActiveBranch(AccessScope scope, UUID branchId) {
        List<String> st = jdbc.queryForList("select status from branch where id = :id and organization_id = :o",
                new MapSqlParameterSource("id", branchId).addValue("o", scope.organizationId()), String.class);
        if (st.isEmpty() || !scope.canSeeBranch(branchId)) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        if (!"ACTIVE".equals(st.get(0))) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, st.get(0));
        }
    }

    public String personName(UUID personId) {
        if (personId == null) {
            return null;
        }
        List<String> n = jdbc.queryForList("select trim(first_name || ' ' || last_name) from person where id = :id", new MapSqlParameterSource("id", personId), String.class);
        return n.isEmpty() ? "" : n.get(0);
    }

    /** El docente debe ser una persona activa y adulta de la organización [V6]. */
    public void assertActiveAdult(UUID orgId, UUID personId) {
        List<Object[]> r = jdbc.query("select status, birth_date from person where id = :id and organization_id = :o",
                new MapSqlParameterSource("id", personId).addValue("o", orgId), (rs, i) -> new Object[]{rs.getString(1), rs.getDate(2)});
        if (r.isEmpty()) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        String status = (String) r.get(0)[0];
        java.sql.Date birth = (java.sql.Date) r.get(0)[1];
        boolean adult = birth != null && Period.between(birth.toLocalDate(), LocalDate.now(clock)).getYears() >= ADULT_AGE;
        if (!"ACTIVE".equals(status) || !adult) {
            throw new Exceptions("error.training.teacherInvalid", HttpStatus.UNPROCESSABLE_ENTITY);
        }
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
}
