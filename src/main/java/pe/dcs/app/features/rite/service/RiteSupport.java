package pe.dcs.app.features.rite.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.util.Exceptions;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/** Utilidades comunes de M08: módulo por tipo, zona horaria, datos mínimos de personas y condiciones de visibilidad. */
@Component
@RequiredArgsConstructor
public class RiteSupport {

    public static final String MEMBERSHIP = "MEMBERSHIP";
    public static final String BAPTISM = "BAPTISM";
    public static final String MARRIAGE = "MARRIAGE";
    public static final String DEDICATION = "DEDICATION";
    static final int ADULT_AGE = 18;

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    /** Código del módulo del catálogo de M03 que gobierna cada tipo. */
    public static String moduleOf(String type) {
        return switch (type) {
            case MEMBERSHIP -> "MEMBERSHIP";
            case BAPTISM -> "BAPTISM";
            case MARRIAGE -> "MARRIAGE";
            case DEDICATION -> "CHILD_DEDICATION";
            default -> throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "tipo");
        };
    }

    public static String typeOfModule(String module) {
        return "CHILD_DEDICATION".equals(module) ? DEDICATION : module;
    }

    /** Persona con lo necesario para validar un rito. */
    public record PersonInfo(UUID id, String name, String status, LocalDate birthDate, UUID branchId, String maritalStatus, boolean anonymized) {
        boolean active() {
            return "ACTIVE".equals(status) && !anonymized;
        }
    }

    /** Persona de la organización (sin filtrar sedes: el llamador decide); 404 si no existe. */
    public PersonInfo person(UUID orgId, UUID id) {
        if (id == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "persona");
        }
        return jdbc.query("select id, trim(first_name || ' ' || last_name), status, birth_date, primary_branch_id, marital_status, anonymized_at is not null"
                        + " from person where id = :id and organization_id = :o", new MapSqlParameterSource("id", id).addValue("o", orgId),
                (rs, i) -> new PersonInfo((UUID) rs.getObject(1), rs.getString(2), rs.getString(3),
                        rs.getDate(4) == null ? null : rs.getDate(4).toLocalDate(), (UUID) rs.getObject(5), rs.getString(6), rs.getBoolean(7)))
                .stream().findFirst().orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    /** Persona activa y dentro del alcance de sedes de quien opera (para crear o cambiar registros). */
    public PersonInfo activeVisible(AccessScope scope, UUID id) {
        PersonInfo p = person(scope.organizationId(), id);
        if (!scope.allBranches()) {
            MapSqlParameterSource ps = new MapSqlParameterSource("id", id);
            Integer n = jdbc.queryForObject("select count(*) from person a where a.id = :id and " + visiblePerson(scope, ps, "a"), ps, Integer.class);
            if (n == null || n == 0) {
                throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
            }
        }
        if (!p.active()) {
            throw new Exceptions("error.rite.personInactive", HttpStatus.UNPROCESSABLE_ENTITY, p.name());
        }
        return p;
    }

    private static String visiblePerson(AccessScope scope, MapSqlParameterSource ps, String a) {
        List<UUID> ids = scope.branchIds() == null || scope.branchIds().isEmpty() ? List.of(new UUID(0, 0)) : List.copyOf(scope.branchIds());
        ps.addValue("scopeBranches", ids);
        return "(" + a + ".primary_branch_id in (:scopeBranches) or exists (select 1 from user_access ua where ua.person_id = " + a
                + ".id and ua.branch_id in (:scopeBranches) and ua.status in ('ACTIVE','INVITED')))";
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

    /** Sede de trabajo del registro: la principal de la persona si el actor la ve; si no, su sede activa; si tampoco, la primera de su alcance. */
    public UUID branchFor(AccessScope scope, UUID activeBranchId, PersonInfo p) {
        if (p.branchId() != null && scope.canSeeBranch(p.branchId())) {
            return p.branchId();
        }
        if (activeBranchId != null && scope.canSeeBranch(activeBranchId)) {
            return activeBranchId;
        }
        if (p.branchId() != null) {
            return p.branchId();
        }
        throw new Exceptions("error.rite.branchRequired", HttpStatus.UNPROCESSABLE_ENTITY);
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

    static Integer age(LocalDate birth, LocalDate today) {
        return birth == null ? null : Period.between(birth, today).getYears();
    }

    /** Sin fecha de nacimiento se trata como adulto. */
    static boolean minor(LocalDate birth, LocalDate today) {
        Integer a = age(birth, today);
        return a != null && a < ADULT_AGE;
    }

    /** Tutores activos del hogar de una persona (guardian = true). */
    public List<UUID> householdGuardians(UUID personId) {
        return jdbc.query("select g.person_id from household_member c join household_member g on g.household_id = c.household_id and g.left_at is null and g.guardian"
                        + " where c.person_id = :p and c.left_at is null and g.person_id <> :p", new MapSqlParameterSource("p", personId),
                (rs, i) -> (UUID) rs.getObject(1));
    }

    /** Escritura: la fila debe estar en una sede del alcance de quien opera. */
    public static String writable(AccessScope scope, MapSqlParameterSource ps, String a) {
        StringBuilder sb = new StringBuilder(a + ".organization_id = :org");
        ps.addValue("org", scope.organizationId());
        if (!scope.allBranches()) {
            List<UUID> ids = scope.branchIds() == null || scope.branchIds().isEmpty() ? List.of(new UUID(0, 0)) : List.copyOf(scope.branchIds());
            ps.addValue("scopeBranches", ids);
            sb.append(" and ").append(a).append(".branch_id in (:scopeBranches)");
        }
        return sb.toString();
    }

    /**
     * Lectura (M21): la de {@link #writable} más lo que abre la regla de visibilidad de la organización para el módulo: ORGANIZATION = todas las
     * filas; PERSON_HISTORY = personas que pasaron por sus sedes; APPROVAL_REQUIRED = personas con autorización vigente para su sede.
     */
    public static String readable(String module, AccessScope scope, MapSqlParameterSource ps, String a) {
        String base = writable(scope, ps, a);
        if (scope.allBranches()) {
            return base;
        }
        String rule = "exists (select 1 from data_access_rule r where r.organization_id = " + a + ".organization_id and r.module_code = '" + module
                + "' and r.enabled and r.scope = ";
        return "(" + base
                + " or (" + a + ".organization_id = :org and (" + rule + "'ORGANIZATION')"
                + " or (" + rule + "'PERSON_HISTORY') and exists (select 1 from person_branch pb where pb.person_id = " + a + ".person_id and pb.branch_id in (:scopeBranches)))"
                + " or (" + rule + "'APPROVAL_REQUIRED') and exists (select 1 from visibility_grant g where g.person_id = " + a + ".person_id and g.module_code = '" + module
                + "' and g.active and g.visible_until >= current_date and g.target_branch_id in (:scopeBranches))))))";
    }

    static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    static String trim(String s, int max) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
