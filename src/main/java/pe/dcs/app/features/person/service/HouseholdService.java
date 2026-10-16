package pe.dcs.app.features.person.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.organization.dto.AddressDto;
import pe.dcs.app.features.person.dto.HouseholdDtos;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PaginationResponse;
import pe.dcs.app.util.pagination.SortRequest;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * M06 · Hogares. Reglas: [V8] una sola cabeza activa por hogar · [V9] una persona en un solo hogar activo · menores con al
 * menos un tutor adulto en el hogar. Un integrante está activo mientras {@code left_at} sea nulo. Quien no ve a todas las
 * sedes ve los hogares donde hay alguien de su alcance; los demás integrantes salen ocultos (sin datos).
 */
@Service
@RequiredArgsConstructor
public class HouseholdService {

    static final String MODULE = "FAMILY";
    static final String ENTITY = "Household";
    private static final Set<String> ROLES = Set.of("HEAD", "SPOUSE", "CHILD", "RELATIVE", "OTHER");

    private final NamedParameterJdbcTemplate jdbc;
    private final PersonLookupService lookup;
    private final AuditService audit;
    private final Clock clock;

    // ---------------------------------------------------------------- consulta

    @Transactional(readOnly = true)
    public PageResponse<HouseholdDtos.Summary> search(AccessScope scope, HouseholdDtos.Search req) {
        HouseholdDtos.Search.Filters f = req == null || req.filters() == null ? new HouseholdDtos.Search.Filters(null, null, null) : req.filters();
        LocalDate today = LocalDate.now(clock);
        MapSqlParameterSource ps = new MapSqlParameterSource("org", scope.organizationId())
                .addValue("adultBirth", java.sql.Date.valueOf(today.minusYears(PersonScope.ADULT_AGE)));
        StringBuilder w = new StringBuilder("h.organization_id = :org and ").append(visibleHousehold(scope, ps));
        if (f.status() != null && !f.status().isBlank()) {
            String st = f.status().trim().toUpperCase();
            if (!Set.of("ACTIVE", "INACTIVE").contains(st)) {
                throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "estado");
            }
            w.append(" and h.status = :status");
            ps.addValue("status", st);
        }
        if (f.q() != null && !f.q().isBlank()) {
            ps.addValue("like", "%" + f.q().trim().toLowerCase().replace("%", "").replace("_", "") + "%");
            w.append(" and (lower(h.name) like :like or exists (select 1 from household_member m join person p on p.id = m.person_id "
                    + "where m.household_id = h.id and m.left_at is null and (lower(p.first_name || ' ' || p.last_name) like :like or lower(p.doc_number) like :like)))");
        }
        if (f.branchId() != null) {
            ps.addValue("branch", f.branchId());
            w.append(" and exists (select 1 from household_member m join person p on p.id = m.person_id "
                    + "where m.household_id = h.id and m.left_at is null and p.primary_branch_id = :branch)");
        }
        Long total = jdbc.queryForObject("select count(*) from household h where " + w, ps, Long.class);
        int size = req == null || req.pagination() == null ? 10 : Math.max(1, Math.min(req.pagination().getSize(), 100));
        int page = req == null || req.pagination() == null ? 0 : Math.max(0, req.pagination().getPage());
        ps.addValue("lim", size).addValue("off", (long) page * size);
        String headVis = PersonScope.visible(scope, ps, "hp");
        List<HouseholdDtos.Summary> rows = jdbc.query("""
                select h.id, h.name, h.status, h.address_city,
                       (select case when %s then hp.first_name || ' ' || hp.last_name else null end
                          from household_member hm join person hp on hp.id = hm.person_id
                         where hm.household_id = h.id and hm.left_at is null and hm.role = 'HEAD') as head_name,
                       (select count(*) from household_member m where m.household_id = h.id and m.left_at is null) as members,
                       (select count(*) from household_member m join person p on p.id = m.person_id
                         where m.household_id = h.id and m.left_at is null and p.birth_date > :adultBirth) as minors
                  from household h where %s order by %s limit :lim offset :off""".formatted(headVis, w, orderBy(req == null ? null : req.sorts())), ps,
                (rs, i) -> new HouseholdDtos.Summary((UUID) rs.getObject("id"), rs.getString("name"), rs.getString("status"),
                        rs.getString("head_name"), rs.getInt("members"), rs.getInt("minors"), rs.getString("address_city")));
        long t = total == null ? 0 : total;
        return new PageResponse<>(rows, new PaginationResponse((int) t, (int) Math.ceil(t / (double) size), size, page));
    }

    @Transactional(readOnly = true)
    public HouseholdDtos.Response get(AccessScope scope, UUID id) {
        return toResponse(scope, load(scope, id));
    }

    // ---------------------------------------------------------------- alta y edición

    @Transactional
    public HouseholdDtos.Response create(AuthenticatedActor actor, AccessScope scope, HouseholdDtos.Request r) {
        String name = name(r == null ? null : r.name());
        List<HouseholdDtos.MemberRequest> members = r.members() == null ? List.of() : r.members();
        if (members.isEmpty()) {
            throw new Exceptions("error.household.headRequired", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        long heads = members.stream().filter(m -> "HEAD".equalsIgnoreCase(m.role())).count();
        if (heads == 0) {
            throw new Exceptions("error.household.headRequired", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (heads > 1) {
            throw new Exceptions("error.household.headDuplicate", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        Set<UUID> seen = new HashSet<>();
        for (HouseholdDtos.MemberRequest m : members) {
            if (m.personId() == null || !seen.add(m.personId())) {
                throw new Exceptions("error.household.alreadyMember", HttpStatus.UNPROCESSABLE_ENTITY);
            }
        }
        UUID id = UUID.randomUUID();
        AddressDto a = r.address();
        jdbc.update("""
                insert into household (id, organization_id, name, address_line, address_district, address_city, address_region, address_country,
                                       address_reference, status, created_at, created_by, version)
                values (:id, :org, :name, :line, :district, :city, :region, :country, :ref, 'ACTIVE', :at, :by, 0)""",
                addr(new MapSqlParameterSource("id", id).addValue("org", scope.organizationId()).addValue("name", name)
                        .addValue("at", Timestamp.from(clock.instant())).addValue("by", actor.ownerId()), a));
        for (HouseholdDtos.MemberRequest m : members) {
            insertMember(actor, scope, id, m);
        }
        assertRules(id);
        audit.record(new AuditService.Command(MODULE, "CREATE", ENTITY, id, scope.organizationId(), null, diff("name", name, "members", members.size())));
        return get(scope, id);
    }

    @Transactional
    public HouseholdDtos.Response update(AuthenticatedActor actor, AccessScope scope, UUID id, HouseholdDtos.Request r) {
        Row h = load(scope, id);
        String name = name(r == null ? null : r.name());
        int n = jdbc.update("""
                update household set name = :name, address_line = :line, address_district = :district, address_city = :city, address_region = :region,
                       address_country = :country, address_reference = :ref, updated_at = :at, updated_by = :by, version = version + 1
                 where id = :id and organization_id = :org and (:v is null or version = :v)""",
                addr(new MapSqlParameterSource("id", id).addValue("org", scope.organizationId()).addValue("name", name)
                        .addValue("at", Timestamp.from(clock.instant())).addValue("by", actor.ownerId()).addValue("v", r.version()), r.address()));
        if (n == 0) {
            throw new Exceptions("error.common.concurrentUpdate", HttpStatus.CONFLICT);
        }
        audit.record(new AuditService.Command(MODULE, "UPDATE", ENTITY, id, scope.organizationId(), null, diff("from", h.name, "to", name)));
        return get(scope, id);
    }

    @Transactional
    public HouseholdDtos.Response addMember(AuthenticatedActor actor, AccessScope scope, UUID id, HouseholdDtos.MemberRequest m) {
        Row h = load(scope, id);
        assertActive(h);
        if (m == null || "HEAD".equalsIgnoreCase(m.role())) {
            throw new Exceptions("error.household.headDuplicate", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        insertMember(actor, scope, id, m);
        assertRules(id);
        audit.record(new AuditService.Command(MODULE, "MEMBER_ADD", ENTITY, id, scope.organizationId(), null,
                diff("person", m.personId().toString(), "role", role(m.role()))));
        return get(scope, id);
    }

    @Transactional
    public HouseholdDtos.Response updateMember(AuthenticatedActor actor, AccessScope scope, UUID id, UUID personId, HouseholdDtos.MemberRequest m) {
        Row h = load(scope, id);
        assertActive(h);
        Map<String, Object> cur = member(id, personId);
        String newRole = role(m == null ? null : m.role());
        boolean wasHead = "HEAD".equals(cur.get("role"));
        if (newRole.equals("HEAD") && !wasHead) {
            throw new Exceptions("error.household.headDuplicate", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (wasHead && !newRole.equals("HEAD")) {
            throw new Exceptions("error.household.headRequired", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        jdbc.update("update household_member set role = :r, guardian = :g where household_id = :h and person_id = :p and left_at is null",
                new MapSqlParameterSource("r", newRole).addValue("g", Boolean.TRUE.equals(m.guardian())).addValue("h", id).addValue("p", personId));
        assertRules(id);
        audit.record(new AuditService.Command(MODULE, "MEMBER_UPDATE", ENTITY, id, scope.organizationId(), null,
                diff("person", personId.toString(), "role", newRole)));
        return get(scope, id);
    }

    /** Saca a un integrante. La cabeza no sale mientras queden otros (primero se cambia la cabeza); el último integrante disuelve el hogar. */
    @Transactional
    public HouseholdDtos.Response removeMember(AuthenticatedActor actor, AccessScope scope, UUID id, UUID personId) {
        Row h = load(scope, id);
        assertActive(h);
        Map<String, Object> cur = member(id, personId);
        long active = count(id);
        if (active <= 1) {
            closeHousehold(scope, id);
            audit.record(new AuditService.Command(MODULE, "DISSOLVE", ENTITY, id, scope.organizationId(), null, diff("name", h.name)));
            return get(scope, id);
        }
        if ("HEAD".equals(cur.get("role"))) {
            throw new Exceptions("error.household.headRequired", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        jdbc.update("update household_member set left_at = :d where household_id = :h and person_id = :p and left_at is null",
                new MapSqlParameterSource("d", java.sql.Date.valueOf(LocalDate.now(clock))).addValue("h", id).addValue("p", personId));
        assertRules(id);
        audit.record(new AuditService.Command(MODULE, "MEMBER_REMOVE", ENTITY, id, scope.organizationId(), null, diff("person", personId.toString())));
        return get(scope, id);
    }

    /** La cabeza actual pasa al rol indicado (por defecto OTHER) y la persona elegida, que debe ser adulta e integrante, es la nueva cabeza. */
    @Transactional
    public HouseholdDtos.Response changeHead(AuthenticatedActor actor, AccessScope scope, UUID id, HouseholdDtos.HeadRequest r) {
        Row h = load(scope, id);
        assertActive(h);
        if (r == null || r.personId() == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "persona");
        }
        Map<String, Object> next = member(id, r.personId());
        if ("HEAD".equals(next.get("role"))) {
            return get(scope, id);
        }
        if (PersonScope.minor(birth(next), LocalDate.now(clock))) {
            throw new Exceptions("error.household.headAdult", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        String prev = r.previousRole() == null || r.previousRole().isBlank() ? "OTHER" : role(r.previousRole());
        if (prev.equals("HEAD")) {
            throw new Exceptions("error.household.headDuplicate", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        jdbc.update("update household_member set role = :r where household_id = :h and role = 'HEAD' and left_at is null",
                new MapSqlParameterSource("r", prev).addValue("h", id));
        jdbc.update("update household_member set role = 'HEAD' where household_id = :h and person_id = :p and left_at is null",
                new MapSqlParameterSource("h", id).addValue("p", r.personId()));
        assertRules(id);
        audit.record(new AuditService.Command(MODULE, "HEAD_CHANGE", ENTITY, id, scope.organizationId(), null, diff("person", r.personId().toString())));
        return get(scope, id);
    }

    /** Disuelve el hogar: todos los integrantes salen y el hogar queda INACTIVE (se conserva el historial). */
    @Transactional
    public void dissolve(AuthenticatedActor actor, AccessScope scope, UUID id) {
        Row h = load(scope, id);
        assertActive(h);
        closeHousehold(scope, id);
        audit.record(new AuditService.Command(MODULE, "DISSOLVE", ENTITY, id, scope.organizationId(), null, diff("name", h.name)));
    }

    // ---------------------------------------------------------------- interno

    private record Row(UUID id, String name, AddressDto address, String status, Long version, java.time.Instant createdAt) {
    }

    private Row load(AccessScope scope, UUID id) {
        MapSqlParameterSource ps = new MapSqlParameterSource("org", scope.organizationId()).addValue("id", id);
        String vis = visibleHousehold(scope, ps);
        return jdbc.query("""
                select h.id, h.name, h.status, h.version, h.created_at, h.address_line, h.address_district, h.address_city, h.address_region,
                       h.address_country, h.address_reference
                  from household h where h.organization_id = :org and h.id = :id and """ + " " + vis, ps,
                (rs, i) -> new Row((UUID) rs.getObject("id"), rs.getString("name"),
                        new AddressDto(rs.getString("address_line"), rs.getString("address_district"), rs.getString("address_city"),
                                rs.getString("address_region"), rs.getString("address_country"), rs.getString("address_reference")),
                        rs.getString("status"), rs.getLong("version"), rs.getTimestamp("created_at").toInstant()))
                .stream().findFirst().orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    private HouseholdDtos.Response toResponse(AccessScope scope, Row h) {
        LocalDate today = LocalDate.now(clock);
        MapSqlParameterSource ps = new MapSqlParameterSource("h", h.id);
        String vis = PersonScope.visible(scope, ps, "p");
        List<HouseholdDtos.Member> members = jdbc.query("""
                select m.person_id, p.first_name, p.last_name, p.doc_number, p.birth_date, p.status, p.primary_branch_id, m.role, m.guardian,
                       m.joined_at, (%s) as visible
                  from household_member m join person p on p.id = m.person_id
                 where m.household_id = :h and m.left_at is null
                 order by case m.role when 'HEAD' then 0 when 'SPOUSE' then 1 when 'CHILD' then 2 else 3 end, p.birth_date nulls last, p.first_name"""
                .formatted(vis), ps, (rs, i) -> {
            java.sql.Date bd = rs.getDate("birth_date");
            LocalDate birth = bd == null ? null : bd.toLocalDate();
            boolean visible = rs.getBoolean("visible");
            boolean minor = PersonScope.minor(birth, today);
            return new HouseholdDtos.Member((UUID) rs.getObject("person_id"),
                    visible ? (rs.getString("last_name") + ", " + rs.getString("first_name")).trim() : null,
                    visible ? rs.getString("doc_number") : null, visible ? PersonScope.age(birth, today) : null, minor,
                    visible ? rs.getString("status") : null, visible ? (UUID) rs.getObject("primary_branch_id") : null,
                    rs.getString("role"), rs.getBoolean("guardian"), rs.getDate("joined_at").toLocalDate(), !visible);
        });
        boolean anyMinor = members.stream().anyMatch(HouseholdDtos.Member::minor);
        boolean adultGuardian = members.stream().anyMatch(m -> m.guardian() && !m.minor());
        return new HouseholdDtos.Response(h.id, h.name, h.address, h.status, members, anyMinor && !adultGuardian, h.version, h.createdAt);
    }

    private void insertMember(AuthenticatedActor actor, AccessScope scope, UUID householdId, HouseholdDtos.MemberRequest m) {
        if (m == null || m.personId() == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "persona");
        }
        String role = role(m.role());
        PersonLookupService.PersonMin p = lookup.getVisible(scope, m.personId());                                   // 404 si está fuera del alcance
        if (!"ACTIVE".equals(p.status())) {
            throw new Exceptions("error.household.memberInactive", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (role.equals("HEAD") && PersonScope.minor(p.birthDate(), LocalDate.now(clock))) {
            throw new Exceptions("error.household.headAdult", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        Boolean already = jdbc.queryForObject("select exists(select 1 from household_member where person_id = :p and left_at is null)",
                new MapSqlParameterSource("p", p.id()), Boolean.class);
        if (Boolean.TRUE.equals(already)) {
            throw new Exceptions("error.household.alreadyMember", HttpStatus.UNPROCESSABLE_ENTITY);                // [V9]
        }
        try {
            jdbc.update("""
                    insert into household_member (id, household_id, person_id, role, guardian, joined_at, created_at, created_by)
                    values (:id, :h, :p, :r, :g, :d, :at, :by)""",
                    new MapSqlParameterSource("id", UUID.randomUUID()).addValue("h", householdId).addValue("p", p.id()).addValue("r", role)
                            .addValue("g", Boolean.TRUE.equals(m.guardian())).addValue("d", java.sql.Date.valueOf(LocalDate.now(clock)))
                            .addValue("at", Timestamp.from(clock.instant())).addValue("by", actor.ownerId()));
        } catch (DataIntegrityViolationException e) {
            throw new Exceptions(role.equals("HEAD") ? "error.household.headDuplicate" : "error.household.alreadyMember", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    /** Reglas del conjunto de integrantes activos: una cabeza [V8], menores con tutor adulto, solo adultos como tutores. */
    private void assertRules(UUID householdId) {
        LocalDate today = LocalDate.now(clock);
        List<Object[]> rows = jdbc.query("""
                select m.role, m.guardian, p.birth_date from household_member m join person p on p.id = m.person_id
                 where m.household_id = :h and m.left_at is null""", new MapSqlParameterSource("h", householdId),
                (rs, i) -> new Object[]{rs.getString(1), rs.getBoolean(2), rs.getDate(3) == null ? null : rs.getDate(3).toLocalDate()});
        if (rows.stream().noneMatch(x -> "HEAD".equals(x[0]))) {
            throw new Exceptions("error.household.headRequired", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        boolean anyMinor = false;
        boolean adultGuardian = false;
        for (Object[] x : rows) {
            boolean minor = PersonScope.minor((LocalDate) x[2], today);
            anyMinor |= minor;
            if ((Boolean) x[1]) {
                if (minor) {
                    throw new Exceptions("error.household.guardianAdult", HttpStatus.UNPROCESSABLE_ENTITY);
                }
                adultGuardian = true;
            }
        }
        if (anyMinor && !adultGuardian) {
            throw new Exceptions("error.household.minorNeedsGuardian", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private Map<String, Object> member(UUID householdId, UUID personId) {
        return jdbc.query("""
                select m.role, m.guardian, p.birth_date from household_member m join person p on p.id = m.person_id
                 where m.household_id = :h and m.person_id = :p and m.left_at is null""",
                new MapSqlParameterSource("h", householdId).addValue("p", personId), (rs, i) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("role", rs.getString(1));
                    m.put("guardian", rs.getBoolean(2));
                    m.put("birth", rs.getDate(3) == null ? null : rs.getDate(3).toLocalDate());
                    return m;
                }).stream().findFirst().orElseThrow(() -> new Exceptions("error.household.notMember", HttpStatus.NOT_FOUND));
    }

    private static LocalDate birth(Map<String, Object> m) {
        return (LocalDate) m.get("birth");
    }

    private long count(UUID householdId) {
        Long n = jdbc.queryForObject("select count(*) from household_member where household_id = :h and left_at is null",
                new MapSqlParameterSource("h", householdId), Long.class);
        return n == null ? 0 : n;
    }

    private void closeHousehold(AccessScope scope, UUID id) {
        jdbc.update("update household_member set left_at = :d where household_id = :h and left_at is null",
                new MapSqlParameterSource("d", java.sql.Date.valueOf(LocalDate.now(clock))).addValue("h", id));
        jdbc.update("update household set status = 'INACTIVE', updated_at = :at, version = version + 1 where id = :h and organization_id = :org",
                new MapSqlParameterSource("at", Timestamp.from(clock.instant())).addValue("h", id).addValue("org", scope.organizationId()));
    }

    private static void assertActive(Row h) {
        if (!"ACTIVE".equals(h.status)) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, h.status);
        }
    }

    /** Hogar visible: todos para quien ve todas las sedes; si no, los que tienen algún integrante activo dentro de su alcance. */
    private static String visibleHousehold(AccessScope scope, MapSqlParameterSource ps) {
        if (scope.allBranches()) {
            return "true";
        }
        return "exists (select 1 from household_member vm join person vp on vp.id = vm.person_id where vm.household_id = h.id and vm.left_at is null and "
                + PersonScope.visible(scope, ps, "vp") + ")";
    }

    private static String role(String raw) {
        String r = raw == null ? "" : raw.trim().toUpperCase();
        if (!ROLES.contains(r)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "rol");
        }
        return r;
    }

    private static String name(String raw) {
        String n = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
        if (n.length() < 2) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "nombre del hogar");
        }
        if (n.length() > 100) {
            throw new Exceptions("error.common.tooLong", HttpStatus.BAD_REQUEST, "nombre del hogar", 100);
        }
        return n;
    }

    private static MapSqlParameterSource addr(MapSqlParameterSource ps, AddressDto a) {
        return ps.addValue("line", blank(a == null ? null : a.line())).addValue("district", blank(a == null ? null : a.district()))
                .addValue("city", blank(a == null ? null : a.city())).addValue("region", blank(a == null ? null : a.region()))
                .addValue("country", a == null || blank(a.country()) == null ? null : a.country().trim().toUpperCase())
                .addValue("ref", blank(a == null ? null : a.reference()));
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String orderBy(List<SortRequest> sorts) {
        String key = sorts == null || sorts.isEmpty() ? "name" : String.valueOf(sorts.get(0).getResolvedField());
        String dir = sorts != null && !sorts.isEmpty() && "DESC".equalsIgnoreCase(sorts.get(0).getDirection()) ? " desc" : " asc";
        String expr = switch (key) {
            case "status" -> "h.status" + dir;
            case "createdAt" -> "h.created_at" + dir;
            default -> "lower(h.name)" + dir;
        };
        return expr + ", h.id";
    }

    private static Map<String, Object> diff(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }
}
