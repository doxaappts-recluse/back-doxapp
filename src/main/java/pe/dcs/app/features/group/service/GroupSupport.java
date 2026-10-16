package pe.dcs.app.features.group.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import pe.dcs.app.features.group.dto.GroupDtos;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.RoleType;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Utilidades de M10: alcance de filas (OWN para quien lidera), personas, zona horaria y las reglas de liderazgo y menores [V3, V4, V7]. */
@Component
@RequiredArgsConstructor
public class GroupSupport {

    public static final String MODULE = "SMALL_GROUP";
    static final Set<String> AUDIENCES = Set.of("ADULT", "YOUTH", "MINOR", "MIXED");
    static final Set<String> ROLES = Set.of("LEADER", "COLEADER", "INTERN", "MEMBER");
    static final int ADULT_AGE = 18;

    private final NamedParameterJdbcTemplate jdbc;
    private final GroupRulesService rules;
    private final Clock clock;

    /** Grupo tal como está guardado. */
    public record GroupRow(UUID id, UUID orgId, UUID branchId, String name, String audience, String status, Integer capacity, boolean openToJoin, LocalDate startDate,
                           UUID parentId, long version) {
        boolean minorAudience() {
            return "MINOR".equals(audience) || "YOUTH".equals(audience);
        }
    }

    public record PersonInfo(UUID id, String name, String status, LocalDate birthDate, UUID branchId, boolean anonymized) {
        boolean active() {
            return "ACTIVE".equals(status) && !anonymized;
        }
    }

    // ---------------------------------------------------------------- alcance

    /** Organización, sedes visibles y, para ORG_USER, solo los grupos donde es líder o co-líder (OWN). */
    public static String visible(AccessScope scope, MapSqlParameterSource ps, String a) {
        StringBuilder sb = new StringBuilder(a + ".organization_id = :org");
        ps.addValue("org", scope.organizationId());
        if (!scope.allBranches()) {
            List<UUID> ids = scope.branchIds() == null || scope.branchIds().isEmpty() ? List.of(new UUID(0, 0)) : List.copyOf(scope.branchIds());
            ps.addValue("scopeBranches", ids);
            sb.append(" and ").append(a).append(".branch_id in (:scopeBranches)");
        }
        if (isOwn(scope)) {
            ps.addValue("meOwn", scope.personId() == null ? new UUID(0, 0) : scope.personId());
            sb.append(" and exists (select 1 from group_member ow where ow.group_id = ").append(a).append(".id and ow.person_id = :meOwn and ow.status = 'ACTIVE'")
                    .append(" and ow.role in ('LEADER','COLEADER'))");
        }
        return sb.toString();
    }

    public static boolean isOwn(AccessScope scope) {
        return scope.role() == RoleType.ORG_USER;
    }

    public GroupRow load(AccessScope scope, UUID id) {
        MapSqlParameterSource ps = new MapSqlParameterSource("id", id);
        String w = visible(scope, ps, "g");
        return jdbc.query("select g.* from small_group g where g.id = :id and " + w, ps, (rs, i) -> row(rs)).stream().findFirst()
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    /** Bloquea la fila del grupo (serializa altas de integrantes y cambios de estado). */
    public GroupRow lock(AccessScope scope, UUID id) {
        load(scope, id);
        return jdbc.query("select g.* from small_group g where g.id = :id for update", new MapSqlParameterSource("id", id), (rs, i) -> row(rs)).stream().findFirst()
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    public GroupRow loadRaw(UUID id) {
        return jdbc.query("select g.* from small_group g where g.id = :id", new MapSqlParameterSource("id", id), (rs, i) -> row(rs)).stream().findFirst()
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    private static GroupRow row(java.sql.ResultSet rs) throws java.sql.SQLException {
        java.sql.Date sd = rs.getDate("start_date");
        int cap = rs.getInt("capacity");
        Integer capacity = rs.wasNull() ? null : cap;
        return new GroupRow((UUID) rs.getObject("id"), (UUID) rs.getObject("organization_id"), (UUID) rs.getObject("branch_id"), rs.getString("name"), rs.getString("audience"),
                rs.getString("status"), capacity, rs.getBoolean("open_to_join"), sd == null ? null : sd.toLocalDate(), (UUID) rs.getObject("parent_group_id"),
                rs.getLong("version"));
    }

    // ---------------------------------------------------------------- tiempo y nombres

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

    public String personName(UUID personId) {
        if (personId == null) {
            return null;
        }
        List<String> n = jdbc.queryForList("select trim(first_name || ' ' || last_name) from person where id = :id", new MapSqlParameterSource("id", personId), String.class);
        return n.isEmpty() ? "" : n.get(0);
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

    // ---------------------------------------------------------------- personas

    public PersonInfo person(UUID orgId, UUID id) {
        if (id == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "persona");
        }
        return jdbc.query("select id, trim(first_name || ' ' || last_name), status, birth_date, primary_branch_id, anonymized_at is not null from person where id = :id and organization_id = :o",
                new MapSqlParameterSource("id", id).addValue("o", orgId), (rs, i) -> new PersonInfo((UUID) rs.getObject(1), rs.getString(2), rs.getString(3),
                        rs.getDate(4) == null ? null : rs.getDate(4).toLocalDate(), (UUID) rs.getObject(5), rs.getBoolean(6))).stream().findFirst()
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    /** Persona activa y dentro del alcance de sedes de quien opera (sede principal o acceso en su alcance). */
    public PersonInfo activeVisible(AccessScope scope, UUID id) {
        PersonInfo p = person(scope.organizationId(), id);
        if (!scope.allBranches()) {
            List<UUID> ids = scope.branchIds() == null || scope.branchIds().isEmpty() ? List.of(new UUID(0, 0)) : List.copyOf(scope.branchIds());
            Integer n = jdbc.queryForObject("select count(*) from person a where a.id = :id and (a.primary_branch_id in (:sb) or exists (select 1 from user_access ua"
                    + " where ua.person_id = a.id and ua.branch_id in (:sb) and ua.status in ('ACTIVE','INVITED')))", new MapSqlParameterSource("id", id).addValue("sb", ids), Integer.class);
            if (n == null || n == 0) {
                throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
            }
        }
        if (!p.active()) {
            throw new Exceptions("error.group.personInactive", HttpStatus.UNPROCESSABLE_ENTITY, p.name());
        }
        return p;
    }

    public static boolean minor(LocalDate birth, LocalDate today) {
        return birth != null && Period.between(birth, today).getYears() < ADULT_AGE;
    }

    public static Integer age(LocalDate birth, LocalDate today) {
        return birth == null ? null : Period.between(birth, today).getYears();
    }

    public int activeCount(UUID groupId) {
        Integer n = jdbc.queryForObject("select count(*) from group_member where group_id = :g and status = 'ACTIVE'", new MapSqlParameterSource("g", groupId), Integer.class);
        return n == null ? 0 : n;
    }

    // ---------------------------------------------------------------- reglas de liderazgo

    private record Leader(UUID personId, String role, boolean active, UUID branchId, LocalDate birth) {
    }

    /**
     * [V3] Un grupo activo necesita un líder activo de su sede y, si la organización lo pide, con membresía vigente. [V4] En grupos de menores o
     * jóvenes ningún líder es menor de edad y hay al menos el mínimo de líderes adultos. La segunda parte se exige a todo grupo que tenga
     * integrantes menores. {@code strict} = el grupo está (o va a quedar) activo.
     */
    public void assertLeadership(GroupRow g, boolean strict) {
        GroupDtos.Rules r = rules.get(g.orgId());
        LocalDate today = today(g.branchId());
        List<Leader> leaders = jdbc.query("select m.person_id, m.role, p.status = 'ACTIVE' and p.anonymized_at is null, p.primary_branch_id, p.birth_date from group_member m"
                        + " join person p on p.id = m.person_id where m.group_id = :g and m.status = 'ACTIVE' and m.role in ('LEADER','COLEADER')",
                new MapSqlParameterSource("g", g.id()), (rs, i) -> new Leader((UUID) rs.getObject(1), rs.getString(2), rs.getBoolean(3), (UUID) rs.getObject(4),
                        rs.getDate(5) == null ? null : rs.getDate(5).toLocalDate()));
        if (g.minorAudience() && leaders.stream().anyMatch(l -> minor(l.birth(), today))) {
            throw new Exceptions("error.group.minorLeader", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (!strict) {
            return;
        }
        Leader leader = leaders.stream().filter(l -> "LEADER".equals(l.role())).findFirst().orElse(null);
        if (leader == null || !leader.active()) {
            throw new Exceptions("error.group.leaderRequired", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (!g.branchId().equals(leader.branchId())) {
            throw new Exceptions("error.group.leaderBranch", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (r.leaderRequiresMembership()) {
            Integer n = jdbc.queryForObject("select count(*) from membership where person_id = :p and current and kind = 'MEMBER'", new MapSqlParameterSource("p", leader.personId()), Integer.class);
            if (n == null || n == 0) {
                throw new Exceptions("error.group.leaderNotMember", HttpStatus.UNPROCESSABLE_ENTITY);
            }
        }
        Integer minors = jdbc.queryForObject("select count(*) from group_member m join person p on p.id = m.person_id where m.group_id = :g and m.status = 'ACTIVE'"
                + " and p.birth_date is not null and p.birth_date > :limit", new MapSqlParameterSource("g", g.id()).addValue("limit", java.sql.Date.valueOf(today.minusYears(ADULT_AGE))), Integer.class);
        if (g.minorAudience() || (minors != null && minors > 0)) {
            long adults = leaders.stream().filter(l -> !minor(l.birth(), today)).count();
            if (adults < r.minAdultLeadersMinors()) {
                throw new Exceptions("error.group.minorsRule", HttpStatus.UNPROCESSABLE_ENTITY, r.minAdultLeadersMinors());
            }
        }
    }

    /** ¿Existe el código en el catálogo GROUP_CATEGORY (base o de la organización) y está activo? */
    public boolean categoryExists(UUID orgId, String code) {
        Integer n = jdbc.queryForObject("select count(*) from catalog_item where type = 'GROUP_CATEGORY' and code = :c and active and (organization_id is null or organization_id = :o)",
                new MapSqlParameterSource("c", code).addValue("o", orgId), Integer.class);
        return n != null && n > 0;
    }
}
