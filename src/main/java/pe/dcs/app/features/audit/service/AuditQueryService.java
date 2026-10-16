package pe.dcs.app.features.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.audit.dto.AuditEventResponse;
import pe.dcs.app.features.audit.dto.AuditFilterOptions;
import pe.dcs.app.features.audit.dto.AuditSearchRequest;
import pe.dcs.app.features.module.domain.AppModule;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AuthorizationService;
import pe.dcs.app.security.authz.ModuleCatalog;
import pe.dcs.app.shared.audit.AuditEvent;
import pe.dcs.app.shared.audit.AuditEventRepository;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.shared.export.XlsxWriter;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.RoleType;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PaginationRequest;
import pe.dcs.app.util.pagination.PaginationResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * M22 · consulta de la auditoría. Tres alcances:
 * plataforma (lo que hizo su personal y lo que no pertenece a una organización; solo SYSTEM_ADMIN),
 * organización (todo lo de su organización; ORG_ADMIN) y sede (solo lo de sus sedes; ORG_BRANCH_ADMIN, otra sede → 404).
 * La auditoría no se modifica ni se borra desde aquí (V6); exportar es exclusivo del ORG_ADMIN (X).
 */
@Service
@RequiredArgsConstructor
public class AuditQueryService {

    static final int MAX_EXPORT_ROWS = 50_000;
    static final Duration MAX_RANGE = Duration.ofDays(366);
    static final Duration DEFAULT_RANGE = Duration.ofDays(30);
    private static final int MAX_PAGE_SIZE = 100;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AuditEventRepository events;
    private final NameLookup names;
    private final AuditMasker masker;
    private final AuthorizationService authz;
    private final ModuleCatalog catalog;
    private final AuditService audit;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Clock clock;

    // ------------------------------------------------------------------ plataforma

    @Transactional(readOnly = true)
    public PageResponse<AuditEventResponse> searchPlatform(AuthenticatedActor actor, AuditSearchRequest req) {
        requireSystemAdmin(actor);
        AuditSearchRequest.Filters f = filters(req);
        return page(actor, platformSpec(f), req);
    }

    @Transactional(readOnly = true)
    public AuditEventResponse detailPlatform(AuthenticatedActor actor, long id) {
        requireSystemAdmin(actor);
        AuditEvent e = events.findOne(platformScope().and((root, q, cb) -> cb.equal(root.get("id"), id)))
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
        return toResponses(actor, List.of(e), true).get(0);
    }

    @Transactional(readOnly = true)
    public AuditFilterOptions filtersPlatform(AuthenticatedActor actor) {
        requireSystemAdmin(actor);
        return options(" where (actor_type = 'STAFF' or organization_id is null)", new Object[0]);
    }

    // ------------------------------------------------------------------ organización / sede

    @Transactional(readOnly = true)
    public PageResponse<AuditEventResponse> searchOrg(AuthenticatedActor actor, AuditSearchRequest req) {
        AuditSearchRequest.Filters f = filters(req);
        return page(actor, orgSpec(actor, f), req);
    }

    @Transactional(readOnly = true)
    public AuditEventResponse detailOrg(AuthenticatedActor actor, long id) {
        AuditEvent e = events.findOne(orgScope(actor).and((root, q, cb) -> cb.equal(root.get("id"), id)))
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
        return toResponses(actor, List.of(e), true).get(0);
    }

    @Transactional(readOnly = true)
    public AuditFilterOptions filtersOrg(AuthenticatedActor actor) {
        if (actor.role() == RoleType.ORG_BRANCH_ADMIN) {
            if (actor.branchIds().isEmpty()) {
                return new AuditFilterOptions(List.of(), List.of(), List.of());
            }
            String in = actor.branchIds().stream().map(b -> "'" + b + "'").collect(Collectors.joining(","));
            return options(" where organization_id = ? and branch_id in (" + in + ")", new Object[]{actor.organizationId()});
        }
        return options(" where organization_id = ?", new Object[]{actor.organizationId()});
    }

    /** Exporta a XLSX (X): solo el ORG_ADMIN (ORG_BRANCH_ADMIN recibe X por rol pero aquí X exige delegación que no existe para sede). */
    @Transactional
    public Export exportOrg(AuthenticatedActor actor, AuditSearchRequest req) {
        if (actor.role() != RoleType.ORG_ADMIN) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
        AuditSearchRequest.Filters f = filters(req);
        Specification<AuditEvent> spec = orgSpec(actor, f);
        long total = events.count(spec);
        if (total > MAX_EXPORT_ROWS) {
            throw new Exceptions("error.audit.exportTooLarge", HttpStatus.UNPROCESSABLE_ENTITY, MAX_EXPORT_ROWS);
        }
        List<AuditEvent> rows = events.findAll(spec, Sort.by(Sort.Direction.DESC, "at").and(Sort.by(Sort.Direction.DESC, "id")));
        List<AuditEventResponse> mapped = toResponses(actor, rows, true);
        String tz = jdbc.queryForObject("select timezone from organization where id = ?", String.class, actor.organizationId());
        ZoneId zone = ZoneId.of(tz == null ? "America/Lima" : tz);

        List<String> headers = List.of("Fecha", "Actor", "Tipo de actor", "Rol", "Módulo", "Acción", "Entidad", "Id de entidad", "Sede", "IP", "Detalle");
        List<List<Object>> data = new ArrayList<>(mapped.size());
        for (AuditEventResponse r : mapped) {
            data.add(java.util.Arrays.asList(
                    STAMP.format(r.at().atZone(zone)), r.actorName(), r.actorType(), r.actorRole(), r.moduleCode(), r.action(),
                    r.entityType(), r.entityId(), r.branchName(), r.ip(), json(r.diff())));
        }
        byte[] bytes = XlsxWriter.write("Auditoría", headers, data);

        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("rows", mapped.size());
        diff.put("from", f.from() == null ? null : f.from().toString());
        diff.put("to", f.to() == null ? null : f.to().toString());
        diff.put("moduleCode", f.moduleCode());
        diff.put("action", f.action());
        audit.record(AuditService.Command.of("AUDIT_LOG", "EXPORT", "AuditEvent", null, diff));
        return new Export(bytes, mapped.size());
    }

    public record Export(byte[] content, int rows) {
    }

    // ------------------------------------------------------------------ alcance y filtros

    private Specification<AuditEvent> platformScope() {
        return (root, q, cb) -> cb.or(cb.equal(root.get("actorType"), "STAFF"), cb.isNull(root.get("organizationId")));
    }

    private Specification<AuditEvent> orgScope(AuthenticatedActor actor) {
        UUID org = actor.organizationId();
        boolean branchAdmin = actor.role() == RoleType.ORG_BRANCH_ADMIN;
        Set<UUID> branches = actor.branchIds();
        return (root, q, cb) -> {
            Predicate byOrg = cb.equal(root.get("organizationId"), org);
            if (!branchAdmin) {
                return byOrg;
            }
            if (branches == null || branches.isEmpty()) {
                return cb.disjunction();
            }
            return cb.and(byOrg, root.get("branchId").in(branches));
        };
    }

    private Specification<AuditEvent> platformSpec(AuditSearchRequest.Filters f) {
        Instant[] range = range(f);
        return platformScope().and(common(f, range)).and((root, q, cb) ->
                f.organizationId() == null ? cb.conjunction() : cb.equal(root.get("organizationId"), f.organizationId()));
    }

    private Specification<AuditEvent> orgSpec(AuthenticatedActor actor, AuditSearchRequest.Filters f) {
        Instant[] range = range(f);
        if (f.branchId() != null) {
            checkBranch(actor, f.branchId());
        }
        return orgScope(actor).and(common(f, range)).and((root, q, cb) ->
                f.branchId() == null ? cb.conjunction() : cb.equal(root.get("branchId"), f.branchId()));
    }

    /** Sede ajena → 404 (V13): para ORG_BRANCH_ADMIN, una sede fuera de las suyas; para todos, una sede de otra organización. */
    private void checkBranch(AuthenticatedActor actor, UUID branchId) {
        if (actor.role() == RoleType.ORG_BRANCH_ADMIN && (actor.branchIds() == null || !actor.branchIds().contains(branchId))) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        Integer own = jdbc.queryForObject("select count(*) from branch where id = ? and organization_id = ?", Integer.class, branchId, actor.organizationId());
        if (own == null || own == 0) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
    }

    private Specification<AuditEvent> common(AuditSearchRequest.Filters f, Instant[] range) {
        return (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.greaterThanOrEqualTo(root.<Instant>get("at"), range[0]));
            ps.add(cb.lessThanOrEqualTo(root.<Instant>get("at"), range[1]));
            if (f.actorId() != null) {
                ps.add(cb.equal(root.get("actorId"), f.actorId()));
            }
            if (has(f.moduleCode())) {
                ps.add(cb.equal(root.get("moduleCode"), f.moduleCode().trim()));
            }
            if (has(f.action())) {
                ps.add(cb.equal(root.get("action"), f.action().trim()));
            }
            if (has(f.entityType())) {
                ps.add(cb.equal(root.get("entityType"), f.entityType().trim()));
            }
            if (has(f.entityId())) {
                ps.add(cb.equal(root.get("entityId"), f.entityId().trim()));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }

    /** Rango [desde, hasta]: por defecto los últimos 30 días; máximo 1 año (422). */
    Instant[] range(AuditSearchRequest.Filters f) {
        Instant now = clock.instant();
        Instant to = f.to() == null ? now : f.to();
        Instant from = f.from() == null ? to.minus(DEFAULT_RANGE) : f.from();
        if (!from.isBefore(to)) {
            throw new Exceptions("error.audit.rangeInvalid", HttpStatus.BAD_REQUEST);
        }
        if (Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
            throw new Exceptions("error.audit.rangeTooLong", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        return new Instant[]{from, to};
    }

    private static AuditSearchRequest.Filters filters(AuditSearchRequest req) {
        return req == null || req.filters() == null
                ? new AuditSearchRequest.Filters(null, null, null, null, null, null, null, null, null) : req.filters();
    }

    private static boolean has(String s) {
        return s != null && !s.isBlank();
    }

    // ------------------------------------------------------------------ armado de respuestas

    private PageResponse<AuditEventResponse> page(AuthenticatedActor actor, Specification<AuditEvent> spec, AuditSearchRequest req) {
        PaginationRequest p = req == null || req.pagination() == null ? new PaginationRequest() : req.pagination();
        int size = Math.min(Math.max(p.getSize(), 1), MAX_PAGE_SIZE);
        int number = Math.max(p.getPage(), 0);
        Page<AuditEvent> page = events.findAll(spec, PageRequest.of(number, size,
                Sort.by(Sort.Direction.DESC, "at").and(Sort.by(Sort.Direction.DESC, "id"))));
        return new PageResponse<>(toResponses(actor, page.getContent(), false),
                new PaginationResponse((int) page.getTotalElements(), page.getTotalPages(), page.getSize(), page.getNumber()));
    }

    List<AuditEventResponse> toResponses(AuthenticatedActor viewer, List<AuditEvent> rows, boolean detail) {
        Set<UUID> personIds = rows.stream().filter(e -> "PERSON".equals(e.getActorType())).map(AuditEvent::getActorId).collect(Collectors.toSet());
        Set<UUID> staffIds = rows.stream().filter(e -> "STAFF".equals(e.getActorType())).map(AuditEvent::getActorId).collect(Collectors.toSet());
        Map<UUID, String> people = names.people(personIds);
        Map<UUID, String> staff = names.staff(staffIds);
        Map<UUID, String> orgs = names.organizations(rows.stream().map(AuditEvent::getOrganizationId).collect(Collectors.toSet()));
        Map<UUID, String> branches = names.branches(rows.stream().map(AuditEvent::getBranchId).collect(Collectors.toSet()));
        Map<String, Boolean> canSee = new HashMap<>();

        List<AuditEventResponse> out = new ArrayList<>(rows.size());
        for (AuditEvent e : rows) {
            String actorName = switch (e.getActorType()) {
                case "PERSON" -> people.get(e.getActorId());
                case "STAFF" -> staff.get(e.getActorId());
                default -> null;
            };
            AuditMasker.Masked m = new AuditMasker.Masked(null, false);
            if (detail) {
                boolean h = canSee.computeIfAbsent(e.getModuleCode(), c -> catalog.find(c) != null && authz.effectiveActions(viewer, c).contains("H"));
                m = masker.apply(e.getDiff(), e.getModuleCode(), h);
            }
            out.add(new AuditEventResponse(e.getId(), e.getAt(), e.getActorType(), e.getActorId(), actorName, e.getActorRole(),
                    e.getOrganizationId(), orgs.get(e.getOrganizationId()), e.getBranchId(), branches.get(e.getBranchId()),
                    e.getModuleCode(), e.getAction(), e.getEntityType(), e.getEntityId(),
                    detail ? m.diff() : null, m.masked(), detail ? e.getIp() : null, detail ? e.getUserAgent() : null, e.getAssistedGrantId()));
        }
        return out;
    }

    private AuditFilterOptions options(String where, Object[] args) {
        List<String> moduleCodes = jdbc.queryForList("select distinct module_code from audit_event" + where + " order by 1", String.class, args);
        List<String> actions = jdbc.queryForList("select distinct action from audit_event" + where + " order by 1", String.class, args);
        List<String> types = jdbc.queryForList("select distinct entity_type from audit_event" + where
                + (where.isEmpty() ? " where" : " and") + " entity_type is not null order by 1", String.class, args);
        List<AuditFilterOptions.ModuleOption> modules = new ArrayList<>();
        for (String c : moduleCodes) {
            AppModule m = catalog.find(c);
            modules.add(new AuditFilterOptions.ModuleOption(c, m == null ? c : m.getNameEs(), m == null ? c : m.getNameEn()));
        }
        return new AuditFilterOptions(modules, actions, types);
    }

    private String json(Map<String, Object> diff) {
        if (diff == null) {
            return null;
        }
        try {
            return mapper.writeValueAsString(diff);
        } catch (Exception e) {
            return diff.toString();
        }
    }

    private void requireSystemAdmin(AuthenticatedActor actor) {
        if (actor.role() != RoleType.SYSTEM_ADMIN) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
    }
}
