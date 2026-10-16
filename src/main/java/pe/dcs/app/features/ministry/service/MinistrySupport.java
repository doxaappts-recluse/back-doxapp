package pe.dcs.app.features.ministry.service;

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
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/** Utilidades de M11: alcance de filas (OWN para quien lidera), carga de ministerios por sede y estado de la verificación (screening) [V6]. */
@Component
@RequiredArgsConstructor
public class MinistrySupport {

    public static final String MODULE = "MINISTRY";
    public static final String DEFAULT_SCREENING = "BACKGROUND";

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    /** Ministerio de una sede tal como está guardado (con lo que necesita el ministerio de la estructura). */
    public record BranchMinistryRow(UUID id, UUID orgId, UUID ministryId, UUID branchId, UUID leaderId, String status, String ministryStatus, String ministryName,
                                    boolean requiresScreening, String screeningType, boolean adultOnly, long version) {
        public boolean open() {
            return "ACTIVE".equals(status) && "ACTIVE".equals(ministryStatus);
        }
    }

    // ---------------------------------------------------------------- alcance

    /** Organización, sedes visibles y, para ORG_USER, solo los ministerios que lidera (OWN). {@code a} es el alias de branch_ministry. */
    public static String visibleBm(AccessScope scope, MapSqlParameterSource ps, String a) {
        StringBuilder sb = new StringBuilder(a + ".organization_id = :org");
        ps.addValue("org", scope.organizationId());
        if (!scope.allBranches()) {
            List<UUID> ids = scope.branchIds() == null || scope.branchIds().isEmpty() ? List.of(new UUID(0, 0)) : List.copyOf(scope.branchIds());
            ps.addValue("scopeBranches", ids);
            sb.append(" and ").append(a).append(".branch_id in (:scopeBranches)");
        }
        if (isOwn(scope)) {
            ps.addValue("meOwn", scope.personId() == null ? new UUID(0, 0) : scope.personId());
            sb.append(" and ").append(a).append(".leader_person_id = :meOwn");
        }
        return sb.toString();
    }

    public static boolean isOwn(AccessScope scope) {
        return scope.role() == RoleType.ORG_USER;
    }

    /** Solo el administrador de la organización cambia la estructura (nivel N2). */
    public static void requireOrgAdmin(AccessScope scope) {
        if (scope.role() != RoleType.ORG_ADMIN) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
    }

    private static final String BM_SELECT = "select b.id, b.organization_id, b.ministry_id, b.branch_id, b.leader_person_id, b.status, m.status as mstatus, m.name, m.requires_screening,"
            + " m.screening_type, m.adult_only, b.version from branch_ministry b join ministry m on m.id = b.ministry_id";

    private static BranchMinistryRow bm(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new BranchMinistryRow((UUID) rs.getObject(1), (UUID) rs.getObject(2), (UUID) rs.getObject(3), (UUID) rs.getObject(4), (UUID) rs.getObject(5), rs.getString(6),
                rs.getString(7), rs.getString(8), rs.getBoolean(9), rs.getString(10), rs.getBoolean(11), rs.getLong(12));
    }

    public BranchMinistryRow loadBm(AccessScope scope, UUID id) {
        MapSqlParameterSource ps = new MapSqlParameterSource("id", id);
        String w = visibleBm(scope, ps, "b");
        return jdbc.query(BM_SELECT + " where b.id = :id and " + w, ps, (rs, i) -> bm(rs)).stream().findFirst()
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    /** Bloquea la fila del ministerio de la sede (serializa asignaciones y cambios). */
    public BranchMinistryRow lockBm(AccessScope scope, UUID id) {
        loadBm(scope, id);
        jdbc.queryForList("select id from branch_ministry where id = :id for update", new MapSqlParameterSource("id", id));
        return loadBmRaw(id);
    }

    public BranchMinistryRow loadBmRaw(UUID id) {
        return jdbc.query(BM_SELECT + " where b.id = :id", new MapSqlParameterSource("id", id), (rs, i) -> bm(rs)).stream().findFirst()
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    // ---------------------------------------------------------------- tiempo y nombres

    /** Hoy en la zona horaria de la organización. */
    public LocalDate orgToday(UUID orgId) {
        List<String> z = jdbc.queryForList("select timezone from organization where id = :o", new MapSqlParameterSource("o", orgId), String.class);
        try {
            return LocalDate.now(clock.withZone(z.isEmpty() || z.get(0) == null ? ZoneId.of("America/Lima") : ZoneId.of(z.get(0))));
        } catch (RuntimeException e) {
            return LocalDate.now(clock.withZone(ZoneId.of("America/Lima")));
        }
    }

    // ---------------------------------------------------------------- verificación (screening)

    /**
     * Estado de la verificación que exige un ministerio hoy: NOT_REQUIRED · OK (hay una CLEARED vigente; «vigente» = sin vencimiento o con vencimiento posterior a hoy) ·
     * PENDING · EXPIRED · MISSING. {@code type} null = cualquier tipo.
     */
    public String screeningState(UUID personId, boolean requires, String type, LocalDate today) {
        if (!requires) {
            return "NOT_REQUIRED";
        }
        MapSqlParameterSource ps = new MapSqlParameterSource("p", personId);
        String sql = "select status, expires_at from person_screening where person_id = :p";
        if (type != null && !type.isBlank()) {
            sql += " and type = :t";
            ps.addValue("t", type);
        }
        boolean pending = false;
        boolean expired = false;
        for (Object[] r : jdbc.query(sql, ps, (rs, i) -> new Object[]{rs.getString(1), rs.getDate(2) == null ? null : rs.getDate(2).toLocalDate()})) {
            String st = (String) r[0];
            LocalDate exp = (LocalDate) r[1];
            if ("CLEARED".equals(st) && (exp == null || exp.isAfter(today))) {
                return "OK";
            }
            if ("PENDING".equals(st)) {
                pending = true;
            } else if ("EXPIRED".equals(st) || "CLEARED".equals(st)) {
                expired = true;
            }
        }
        return pending ? "PENDING" : expired ? "EXPIRED" : "MISSING";
    }

    /** Cláusula SQL con el estado efectivo de una fila de person_screening (alias {@code s}); requiere el parámetro :today. */
    public static final String EFFECTIVE_STATUS = "(case when s.status = 'CLEARED' and s.expires_at is not null and s.expires_at <= :today then 'EXPIRED' else s.status end)";

    /** Requisitos para ocupar un ministerio de una sede: mayoría de edad si es solo adultos o si el cargo es de liderazgo, y verificación vigente si el ministerio la exige [V6]. */
    public void assertEligible(BranchMinistryRow bm, PersonRef p, boolean leaderRole, LocalDate today) {
        if ((bm.adultOnly() || leaderRole) && p.minor(today)) {
            throw new Exceptions("error.ministry.adultOnly", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        String st = screeningState(p.id(), bm.requiresScreening(), bm.screeningType(), today);
        if (!"OK".equals(st) && !"NOT_REQUIRED".equals(st)) {
            throw new Exceptions("error.ministry.screeningRequired", HttpStatus.UNPROCESSABLE_ENTITY, p.name());
        }
    }

    public String personName(UUID personId) {
        if (personId == null) {
            return null;
        }
        List<String> n = jdbc.queryForList("select trim(first_name || ' ' || last_name) from person where id = :id", new MapSqlParameterSource("id", personId), String.class);
        return n.isEmpty() ? "" : n.get(0);
    }

    public String branchName(UUID branchId) {
        List<String> n = jdbc.queryForList("select name from branch where id = :id", new MapSqlParameterSource("id", branchId), String.class);
        return n.isEmpty() ? "" : n.get(0);
    }

    // ---------------------------------------------------------------- personas

    public record PersonRef(UUID id, String name, LocalDate birth, UUID branchId) {
        public boolean minor(LocalDate today) {
            return birth != null && java.time.Period.between(birth, today).getYears() < ADULT_AGE;
        }
    }

    public static final int ADULT_AGE = 18;

    /** Persona de la organización, activa y (si quien opera no ve todas las sedes) dentro de su alcance. */
    public PersonRef activePerson(AccessScope scope, UUID id) {
        if (id == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "persona");
        }
        List<Object[]> r = jdbc.query("select trim(first_name || ' ' || last_name), status, anonymized_at is not null, birth_date, primary_branch_id from person where id = :id and organization_id = :o",
                new MapSqlParameterSource("id", id).addValue("o", scope.organizationId()),
                (rs, i) -> new Object[]{rs.getString(1), rs.getString(2), rs.getBoolean(3), rs.getDate(4) == null ? null : rs.getDate(4).toLocalDate(), rs.getObject(5)});
        if (r.isEmpty()) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        if (!scope.allBranches()) {
            List<UUID> ids = scope.branchIds() == null || scope.branchIds().isEmpty() ? List.of(new UUID(0, 0)) : List.copyOf(scope.branchIds());
            Integer n = jdbc.queryForObject("select count(*) from person a where a.id = :id and (a.primary_branch_id in (:sb) or exists (select 1 from user_access ua"
                    + " where ua.person_id = a.id and ua.branch_id in (:sb) and ua.status in ('ACTIVE','INVITED')))", new MapSqlParameterSource("id", id).addValue("sb", ids), Integer.class);
            if (n == null || n == 0) {
                throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
            }
        }
        Object[] x = r.get(0);
        if (!"ACTIVE".equals(x[1]) || (Boolean) x[2]) {
            throw new Exceptions("error.ministry.personInactive", HttpStatus.UNPROCESSABLE_ENTITY, x[0]);
        }
        return new PersonRef(id, (String) x[0], (LocalDate) x[3], (UUID) x[4]);
    }

    /** Persona activa de la organización sin comprobar alcance (la aprobación ya la decidió quien tiene derecho). */
    public PersonRef activePersonOrg(UUID orgId, UUID id) {
        List<Object[]> r = jdbc.query("select trim(first_name || ' ' || last_name), status, anonymized_at is not null, birth_date, primary_branch_id from person where id = :id and organization_id = :o",
                new MapSqlParameterSource("id", id).addValue("o", orgId),
                (rs, i) -> new Object[]{rs.getString(1), rs.getString(2), rs.getBoolean(3), rs.getDate(4) == null ? null : rs.getDate(4).toLocalDate(), rs.getObject(5)});
        if (r.isEmpty()) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        Object[] x = r.get(0);
        if (!"ACTIVE".equals(x[1]) || (Boolean) x[2]) {
            throw new Exceptions("error.ministry.personInactive", HttpStatus.UNPROCESSABLE_ENTITY, x[0]);
        }
        return new PersonRef(id, (String) x[0], (LocalDate) x[3], (UUID) x[4]);
    }

    public static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    public static String trim(String s, int max, String label) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.trim();
        if (t.length() > max) {
            throw new Exceptions("error.common.tooLong", HttpStatus.BAD_REQUEST, label, max);
        }
        return t;
    }

    public boolean catalogExists(UUID orgId, String type, String code) {
        Integer n = jdbc.queryForObject("select count(*) from catalog_item where type = :t and code = :c and active and (organization_id is null or organization_id = :o)",
                new MapSqlParameterSource("t", type).addValue("c", code).addValue("o", orgId), Integer.class);
        return n != null && n > 0;
    }
}
