package pe.dcs.app.features.facility.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.facility.dto.FacilityDtos;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.AuthorizationService;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PaginationResponse;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * M16 · Espacios (SPACES): alta, edición, cambio de estado (ACTIVE/MAINTENANCE/INACTIVE) [V3] name único por sede, capacidad ≥1.
 * Un espacio en MAINTENANCE/INACTIVE no admite reservas nuevas [V6] (lo valida {@link ReservationService}).
 */
@Service
@RequiredArgsConstructor
public class SpaceService {

    private static final Set<String> STATUSES = Set.of("ACTIVE", "MAINTENANCE", "INACTIVE");
    private static final String ENTITY = "Space";

    private final NamedParameterJdbcTemplate jdbc;
    private final AuthorizationService authz;
    private final FacilitySupport support;
    private final AuditService audit;
    private final Clock clock;

    record Row(UUID id, UUID branchId, String name, String status, int bufferMinutes, boolean requiresApproval) {
    }

    @Transactional(readOnly = true)
    public PageResponse<FacilityDtos.SpaceView> search(AccessScope scope, FacilityDtos.SpaceSearch req) {
        FacilityDtos.SpaceSearch.SpaceFilters f = req == null || req.filters() == null ? new FacilityDtos.SpaceSearch.SpaceFilters(null, null, null, null) : req.filters();
        MapSqlParameterSource ps = new MapSqlParameterSource();
        StringBuilder w = new StringBuilder(FacilitySupport.visibleByBranch(scope, ps, "s"));
        if (f.branchId() != null) {
            w.append(" and s.branch_id = :fb");
            ps.addValue("fb", f.branchId());
        }
        if (FacilitySupport.hasText(f.status())) {
            w.append(" and s.status = :fs");
            ps.addValue("fs", f.status().trim().toUpperCase());
        }
        if (FacilitySupport.hasText(f.typeCode())) {
            w.append(" and s.type_code = :ft");
            ps.addValue("ft", f.typeCode().trim().toUpperCase());
        }
        if (FacilitySupport.hasText(f.q())) {
            w.append(" and lower(s.name) like :q");
            ps.addValue("q", "%" + f.q().trim().toLowerCase() + "%");
        }
        String from = " from space s left join branch b on b.id = s.branch_id where ";
        Long total = jdbc.queryForObject("select count(*)" + from + w, ps, Long.class);
        int size = req == null || req.pagination() == null ? 20 : Math.max(1, Math.min(req.pagination().getSize(), 200));
        int page = req == null || req.pagination() == null ? 0 : Math.max(0, req.pagination().getPage());
        ps.addValue("lim", size).addValue("off", (long) page * size);
        List<FacilityDtos.SpaceView> rows = jdbc.query(SELECT + from + w + " order by b.name, s.name limit :lim offset :off", ps, (rs, i) -> view(rs));
        long t = total == null ? 0 : total;
        return new PageResponse<>(rows, new PaginationResponse((int) t, (int) Math.ceil(t / (double) size), size, page));
    }

    private static final String SELECT = "select s.*, b.name as branch_name";

    @Transactional(readOnly = true)
    public FacilityDtos.SpaceView get(AccessScope scope, UUID id) {
        MapSqlParameterSource ps = new MapSqlParameterSource("id", id);
        String vis = FacilitySupport.visibleByBranch(scope, ps, "s");
        return jdbc.query(SELECT + " from space s left join branch b on b.id = s.branch_id where s.id = :id and " + vis, ps, (rs, i) -> view(rs)).stream()
                .findFirst().orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    /** Uso interno de otros servicios (reservas, asignaciones): datos crudos del espacio sin filtrar por alcance visible. */
    Row raw(UUID id) {
        List<Row> r = jdbc.query("select id, branch_id, name, status, buffer_minutes, requires_approval from space where id = :id", new MapSqlParameterSource("id", id),
                (rs, i) -> new Row((UUID) rs.getObject(1), (UUID) rs.getObject(2), rs.getString(3), rs.getString(4), rs.getInt(5), rs.getBoolean(6)));
        if (r.isEmpty()) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        return r.get(0);
    }

    private Row lock(AccessScope scope, UUID id) {
        get(scope, id);
        return jdbc.query("select id, branch_id, name, status, buffer_minutes, requires_approval from space where id = :id for update", new MapSqlParameterSource("id", id),
                (rs, i) -> new Row((UUID) rs.getObject(1), (UUID) rs.getObject(2), rs.getString(3), rs.getString(4), rs.getInt(5), rs.getBoolean(6))).get(0);
    }

    @Transactional
    public FacilityDtos.SpaceView create(AuthenticatedActor actor, AccessScope scope, FacilityDtos.SpaceRequest r) {
        authz.require(actor, FacilitySupport.MOD_SPACES, Action.C);
        UUID branchId = validateBranch(scope, r == null ? null : r.branchId());
        String name = FacilitySupport.trim(r.name(), 120, "nombre");
        if (name == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "nombre");
        }
        Integer capacity = validCapacity(r.capacity());
        int buffer = r.bufferMinutes() == null ? 0 : r.bufferMinutes();
        if (buffer < 0) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "buffer");
        }
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("insert into space (id, organization_id, branch_id, name, type_code, capacity, equipment, requires_approval, open_from, open_to,"
                            + " buffer_minutes, status, created_at, created_by) values (:id, :o, :b, :n, :ty, :cap, cast(:eq as jsonb), :ra, :of, :ot, :buf,"
                            + " 'ACTIVE', :at, :by)",
                    new MapSqlParameterSource("id", id).addValue("o", scope.organizationId()).addValue("b", branchId).addValue("n", name)
                            .addValue("ty", blank(r.typeCode())).addValue("cap", capacity).addValue("eq", equipmentJson(r.equipment()))
                            .addValue("ra", Boolean.TRUE.equals(r.requiresApproval())).addValue("of", r.openFrom() == null ? null : java.sql.Time.valueOf(r.openFrom()))
                            .addValue("ot", r.openTo() == null ? null : java.sql.Time.valueOf(r.openTo())).addValue("buf", buffer)
                            .addValue("at", Timestamp.from(clock.instant())).addValue("by", scope.personId()));
        } catch (DuplicateKeyException e) {
            throw new Exceptions("error.space.nameTaken", HttpStatus.CONFLICT);                                             // [V3]
        }
        audit.record(new AuditService.Command(FacilitySupport.MOD_SPACES, "CREATE", ENTITY, id, scope.organizationId(), branchId, Map.of("name", name)));
        return get(scope, id);
    }

    @Transactional
    public FacilityDtos.SpaceView update(AuthenticatedActor actor, AccessScope scope, UUID id, FacilityDtos.SpaceRequest r) {
        authz.require(actor, FacilitySupport.MOD_SPACES, Action.E);
        Row cur = lock(scope, id);
        String name = FacilitySupport.trim(r.name(), 120, "nombre");
        if (name == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "nombre");
        }
        Integer capacity = validCapacity(r.capacity());
        int buffer = r.bufferMinutes() == null ? 0 : r.bufferMinutes();
        if (buffer < 0) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "buffer");
        }
        try {
            int n = jdbc.update("update space set name = :n, type_code = :ty, capacity = :cap, equipment = cast(:eq as jsonb), requires_approval = :ra,"
                            + " open_from = :of, open_to = :ot, buffer_minutes = :buf, updated_at = :at, updated_by = :by, version = version + 1"
                            + " where id = :id and (cast(:v as bigint) is null or version = :v)",
                    new MapSqlParameterSource("n", name).addValue("ty", blank(r.typeCode())).addValue("cap", capacity)
                            .addValue("eq", equipmentJson(r.equipment())).addValue("ra", Boolean.TRUE.equals(r.requiresApproval()))
                            .addValue("of", r.openFrom() == null ? null : java.sql.Time.valueOf(r.openFrom()))
                            .addValue("ot", r.openTo() == null ? null : java.sql.Time.valueOf(r.openTo())).addValue("buf", buffer)
                            .addValue("at", Timestamp.from(clock.instant())).addValue("by", scope.personId()).addValue("id", id).addValue("v", r.version()));
            if (n == 0) {
                throw new Exceptions("error.common.concurrentUpdate", HttpStatus.CONFLICT);
            }
        } catch (DuplicateKeyException e) {
            throw new Exceptions("error.space.nameTaken", HttpStatus.CONFLICT);
        }
        audit.record(new AuditService.Command(FacilitySupport.MOD_SPACES, "UPDATE", ENTITY, id, scope.organizationId(), cur.branchId(), Map.of()));
        return get(scope, id);
    }

    @Transactional
    public FacilityDtos.SpaceView setStatus(AuthenticatedActor actor, AccessScope scope, UUID id, String statusRaw) {
        authz.require(actor, FacilitySupport.MOD_SPACES, Action.S);
        Row cur = lock(scope, id);
        String status = statusRaw == null ? null : statusRaw.trim().toUpperCase();
        if (status == null || !STATUSES.contains(status)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "estado");
        }
        jdbc.update("update space set status = :s, updated_at = :at, updated_by = :by, version = version + 1 where id = :id",
                new MapSqlParameterSource("s", status).addValue("at", Timestamp.from(clock.instant())).addValue("by", scope.personId()).addValue("id", id));
        audit.record(new AuditService.Command(FacilitySupport.MOD_SPACES, "STATUS", ENTITY, id, scope.organizationId(), cur.branchId(), Map.of("status", status)));
        return get(scope, id);
    }

    @Transactional
    public void delete(AuthenticatedActor actor, AccessScope scope, UUID id) {
        authz.require(actor, FacilitySupport.MOD_SPACES, Action.D);
        Row cur = lock(scope, id);
        Integer n = jdbc.queryForObject("select count(*) from reservation where space_id = :id and status in ('PENDING','CONFIRMED')",
                new MapSqlParameterSource("id", id), Integer.class);
        if (n != null && n > 0) {
            throw new Exceptions("error.common.hasDependencies", HttpStatus.CONFLICT);
        }
        jdbc.update("delete from space where id = :id", new MapSqlParameterSource("id", id));
        audit.record(new AuditService.Command(FacilitySupport.MOD_SPACES, "DELETE", ENTITY, id, scope.organizationId(), cur.branchId(), Map.of()));
    }

    /** [V33] Estado y sede del espacio, para que otros módulos (eventos, a futuro grupos/dictados) validen antes de enlazar una reserva. */
    @Transactional(readOnly = true)
    public SpaceFacts facts(UUID orgId, UUID spaceId) {
        List<SpaceFacts> r = jdbc.query("select id, branch_id, status from space where id = :id and organization_id = :o",
                new MapSqlParameterSource("id", spaceId).addValue("o", orgId), (rs, i) -> new SpaceFacts((UUID) rs.getObject(1), (UUID) rs.getObject(2), rs.getString(3)));
        if (r.isEmpty()) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        return r.get(0);
    }

    public record SpaceFacts(UUID id, UUID branchId, String status) {
    }

    /** Espacios ACTIVE de una sede visible, para selectores. */
    @Transactional(readOnly = true)
    public List<FacilityDtos.SpaceView> activeOptions(AccessScope scope, UUID branchId) {
        MapSqlParameterSource ps = new MapSqlParameterSource("b", branchId).addValue("org", scope.organizationId());
        return jdbc.query(SELECT + " from space s left join branch b on b.id = s.branch_id where s.organization_id = :org and s.branch_id = :b and s.status = 'ACTIVE' order by s.name",
                ps, (rs, i) -> view(rs));
    }

    // ---------------------------------------------------------------- reglas del espacio (org)

    @Transactional(readOnly = true)
    public FacilityDtos.SpaceRulesView rules(UUID orgId) {
        ensureRules(orgId);
        return jdbc.query("select max_duration_hours, reservation_requesters, max_recurrence, updated_at from space_rules where organization_id = :o",
                new MapSqlParameterSource("o", orgId), (rs, i) -> new FacilityDtos.SpaceRulesView(rs.getInt(1), rs.getString(2), rs.getInt(3),
                        rs.getTimestamp(4) == null ? null : rs.getTimestamp(4).toInstant())).get(0);
    }

    Row2 rulesRaw(UUID orgId) {
        ensureRules(orgId);
        return jdbc.query("select max_duration_hours, max_recurrence, reservation_requesters from space_rules where organization_id = :o",
                new MapSqlParameterSource("o", orgId), (rs, i) -> new Row2(rs.getInt(1), rs.getInt(2), rs.getString(3))).get(0);
    }

    record Row2(int maxDurationHours, int maxRecurrence, String requesters) {
    }

    private void ensureRules(UUID orgId) {
        jdbc.update("insert into space_rules (id, organization_id) values (:id, :o) on conflict (organization_id) do nothing",
                new MapSqlParameterSource("id", UUID.randomUUID()).addValue("o", orgId));
    }

    @Transactional
    public FacilityDtos.SpaceRulesView updateRules(AuthenticatedActor actor, AccessScope scope, FacilityDtos.SpaceRulesRequest r) {
        authz.require(actor, FacilitySupport.MOD_SPACES, Action.E);
        // [spec N2 linea 21] SpaceRules es exclusivo de ORG_ADMIN: E de SPACES también lo tiene el ORG_BRANCH_ADMIN (lo necesita
        // para editar sus propios espacios), así que la restricción de "solo ORG_ADMIN" se revalida aquí — mismo patrón que
        // FinanceRulesService.update() (M15) con fin_rules.
        if (scope.role() != pe.dcs.app.util.enums.RoleType.ORG_ADMIN) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
        ensureRules(scope.organizationId());
        int maxDuration = r.maxDurationHours() == null ? 12 : r.maxDurationHours();
        int maxRecurrence = r.maxRecurrence() == null ? 12 : r.maxRecurrence();
        if (maxDuration < 1 || maxRecurrence < 1) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "reglas");                                 // [V1]
        }
        String requesters = r.reservationRequesters() == null ? "STAFF" : r.reservationRequesters().trim().toUpperCase();
        if (!Set.of("LEADERS", "ANY_MEMBER", "STAFF").contains(requesters)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "solicitantes");
        }
        jdbc.update("update space_rules set max_duration_hours = :d, reservation_requesters = :req, max_recurrence = :rec, updated_at = :at, updated_by = :by"
                        + " where organization_id = :o",
                new MapSqlParameterSource("d", maxDuration).addValue("req", requesters).addValue("rec", maxRecurrence)
                        .addValue("at", Timestamp.from(clock.instant())).addValue("by", scope.personId()).addValue("o", scope.organizationId()));
        audit.record(new AuditService.Command(FacilitySupport.MOD_SPACES, "RULES_UPDATE", "SpaceRules", null, scope.organizationId(), null, Map.of()));
        return rules(scope.organizationId());
    }

    // ---------------------------------------------------------------- helpers

    private UUID validateBranch(AccessScope scope, UUID branchId) {
        if (branchId == null || !scope.canSeeBranch(branchId)) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "sede");
        }
        return branchId;
    }

    private static Integer validCapacity(Integer capacity) {
        if (capacity != null && capacity < 1) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "capacidad");                              // [V3]
        }
        return capacity;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim().toUpperCase();
    }

    private String equipmentJson(List<String> equipment) {
        List<String> clean = new ArrayList<>();
        if (equipment != null) {
            for (String e : equipment) {
                if (e != null && !e.isBlank()) {
                    clean.add(FacilitySupport.trim(e, 60, "equipo"));
                }
            }
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(clean);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return "[]";
        }
    }

    private static List<String> equipmentList(String json) {
        try {
            return json == null ? List.of() : new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() { });
        } catch (Exception e) {
            return List.of();
        }
    }

    private static FacilityDtos.SpaceView view(java.sql.ResultSet rs) throws java.sql.SQLException {
        java.sql.Time of = rs.getTime("open_from");
        java.sql.Time ot = rs.getTime("open_to");
        return new FacilityDtos.SpaceView((UUID) rs.getObject("id"), (UUID) rs.getObject("branch_id"), rs.getString("branch_name"), rs.getString("name"),
                rs.getString("type_code"), (Integer) rs.getObject("capacity"), equipmentList(rs.getString("equipment")), rs.getBoolean("requires_approval"),
                of == null ? null : of.toLocalTime(), ot == null ? null : ot.toLocalTime(), rs.getInt("buffer_minutes"), rs.getString("status"),
                rs.getTimestamp("created_at").toInstant(), rs.getLong("version"));
    }
}
