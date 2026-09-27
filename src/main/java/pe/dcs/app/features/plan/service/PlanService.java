package pe.dcs.app.features.plan.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.contract.domain.ContractRepository;
import pe.dcs.app.features.module.domain.AppModule;
import pe.dcs.app.features.module.domain.AppModuleRepository;
import pe.dcs.app.features.module.domain.ModuleKind;
import pe.dcs.app.features.plan.domain.Plan;
import pe.dcs.app.features.plan.domain.PlanRepository;
import pe.dcs.app.features.plan.domain.PlanStatus;
import pe.dcs.app.features.plan.dto.PlanRequest;
import pe.dcs.app.features.plan.dto.PlanResponse;
import pe.dcs.app.features.plan.dto.PlanSearchRequest;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PageableUtil;
import pe.dcs.app.util.pagination.PaginationResponse;
import pe.dcs.app.util.pagination.SortRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * M03 · Planes (N1): DRAFT → PUBLISHED → RETIRED. [V3] un plan incluye al menos un módulo CONTRACTABLE publicado.
 * Editar un plan no altera los contratos existentes: cada contrato guarda su propia copia (nombre, precio, módulos).
 */
@Service
@RequiredArgsConstructor
public class PlanService {

    static final String MODULE = "PLAN";
    static final String ENTITY = "Plan";
    private static final Pattern CODE = Pattern.compile("^[A-Z0-9_]{3,40}$");
    private static final Pattern CURRENCY = Pattern.compile("^[A-Z]{3}$");

    private final PlanRepository plans;
    private final AppModuleRepository modules;
    private final ContractRepository contracts;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public PageResponse<PlanResponse> search(PlanSearchRequest req) {
        PlanSearchRequest.Filters f = req == null || req.filters() == null ? new PlanSearchRequest.Filters(null, null) : req.filters();
        Specification<Plan> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (f.q() != null && !f.q().isBlank()) {
                String like = "%" + f.q().trim().toLowerCase() + "%";
                ps.add(cb.or(cb.like(cb.lower(root.get("code")), like), cb.like(cb.lower(root.get("name")), like)));
            }
            if (f.status() != null && !f.status().isBlank()) {
                ps.add(cb.equal(root.get("status"), parseStatus(f.status())));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        List<SortRequest> sorts = req == null ? null : req.sorts();
        if (sorts == null || sorts.isEmpty()) {
            SortRequest byName = new SortRequest();
            byName.setKey("name");
            byName.setDirection("ASC");
            sorts = List.of(byName);
        }
        Pageable pageable = PageableUtil.buildPageable(req == null ? null : req.pagination(), sorts, PlanService::sortField);
        Page<Plan> page = plans.findAll(spec, pageable);
        Map<String, AppModule> catalog = catalog();
        List<PlanResponse> content = page.getContent().stream().map(p -> toResponse(p, catalog)).toList();
        return new PageResponse<>(content, new PaginationResponse(
                (int) page.getTotalElements(), page.getTotalPages(), page.getSize(), page.getNumber()));
    }

    /** Planes PUBLISHED, para el selector del formulario de contrato. */
    @Transactional(readOnly = true)
    public List<PlanResponse> published() {
        Map<String, AppModule> catalog = catalog();
        return plans.findAll().stream().filter(p -> p.getStatus() == PlanStatus.PUBLISHED)
                .sorted(java.util.Comparator.comparing(Plan::getName, String.CASE_INSENSITIVE_ORDER))
                .map(p -> toResponse(p, catalog)).toList();
    }

    @Transactional(readOnly = true)
    public PlanResponse get(UUID id) {
        return toResponse(find(id), catalog());
    }

    @Transactional
    public PlanResponse create(PlanRequest req) {
        String code = req.code() == null ? "" : req.code().trim().toUpperCase();
        if (!CODE.matcher(code).matches()) {
            throw new Exceptions("error.plan.codeFormat", HttpStatus.BAD_REQUEST);
        }
        if (plans.codeTaken(code)) {
            throw new Exceptions("error.plan.codeTaken", HttpStatus.CONFLICT);
        }
        Plan p = new Plan();
        p.setCode(code);
        p.setStatus(PlanStatus.DRAFT);
        apply(p, req);
        plans.save(p);
        audit.record(AuditService.Command.of(MODULE, "CREATE", ENTITY, p.getId(), Map.of("code", code, "modules", p.getModuleCodes())));
        return toResponse(p, catalog());
    }

    @Transactional
    public PlanResponse update(UUID id, PlanRequest req) {
        Plan p = find(id);
        if (p.getStatus() == PlanStatus.RETIRED) {
            throw new Exceptions("error.plan.retired", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        Map<String, Object> before = snapshot(p);
        apply(p, req);
        plans.save(p);
        audit.record(AuditService.Command.of(MODULE, "UPDATE", ENTITY, p.getId(), diff(before, snapshot(p))));
        return toResponse(p, catalog());
    }

    @Transactional
    public PlanResponse changeStatus(UUID id, String target) {
        Plan p = find(id);
        PlanStatus to = parseStatus(target);
        if (!allowed(p).contains(to)) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, p.getStatus());
        }
        if (to == PlanStatus.PUBLISHED) {
            validateModules(p.getModuleCodes());                                           // [V3]
        }
        PlanStatus from = p.getStatus();
        p.setStatus(to);
        plans.save(p);
        audit.record(AuditService.Command.of(MODULE, "STATUS", ENTITY, p.getId(), Map.of("from", from.name(), "to", to.name())));
        return toResponse(p, catalog());
    }

    // ---------------------------------------------------------------- reglas

    private void apply(Plan p, PlanRequest req) {
        String currency = req.currency().trim().toUpperCase();
        if (!CURRENCY.matcher(currency).matches()) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "currency");
        }
        if (req.price().compareTo(BigDecimal.ZERO) < 0 || req.price().scale() > 2) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "price");
        }
        Set<String> codes = new LinkedHashSet<>();
        if (req.modules() != null) {
            req.modules().forEach(c -> {
                if (c != null && !c.isBlank()) {
                    codes.add(c.trim().toUpperCase());
                }
            });
        }
        validateModules(codes);
        p.setName(req.name().trim());
        p.setDescription(req.description() == null || req.description().isBlank() ? null : req.description().trim());
        p.setPrice(req.price());
        p.setCurrency(currency);
        p.getModuleCodes().clear();
        p.getModuleCodes().addAll(codes);
    }

    /** [V3] al menos un módulo; todos existentes, CONTRACTABLE y publicados. */
    private void validateModules(Set<String> codes) {
        if (codes.isEmpty()) {
            throw new Exceptions("error.plan.empty", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        for (String c : codes) {
            AppModule m = modules.findById(c).orElse(null);
            if (m == null || m.getKind() != ModuleKind.CONTRACTABLE || !m.isPublished()) {
                throw new Exceptions("error.plan.moduleInvalid", HttpStatus.UNPROCESSABLE_ENTITY, c);
            }
        }
    }

    private static List<PlanStatus> allowed(Plan p) {
        return switch (p.getStatus()) {
            case DRAFT -> List.of(PlanStatus.PUBLISHED);
            case PUBLISHED -> List.of(PlanStatus.RETIRED);
            case RETIRED -> List.of();
        };
    }

    // ---------------------------------------------------------------- utilidades

    private Plan find(UUID id) {
        return plans.findById(id).orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    private Map<String, AppModule> catalog() {
        Map<String, AppModule> m = new LinkedHashMap<>();
        modules.findAll().forEach(x -> m.put(x.getCode(), x));
        return m;
    }

    private PlanResponse toResponse(Plan p, Map<String, AppModule> catalog) {
        List<PlanResponse.ModuleRef> refs = p.getModuleCodes().stream().sorted().map(c -> {
            AppModule m = catalog.get(c);
            return new PlanResponse.ModuleRef(c, m == null ? c : m.getNameEs(), m == null ? c : m.getNameEn(), m == null ? "RETIRED" : m.getStatus());
        }).toList();
        long used = contracts.count((root, q, cb) -> cb.equal(root.get("planId"), p.getId()));
        return new PlanResponse(p.getId(), p.getCode(), p.getName(), p.getDescription(), p.getPrice(), p.getCurrency(), p.getStatus(),
                refs, used, allowed(p), p.getCreatedAt(), p.getUpdatedAt(), p.getVersion());
    }

    private static Map<String, Object> snapshot(Plan p) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("name", p.getName());
        s.put("description", p.getDescription());
        s.put("price", p.getPrice());
        s.put("currency", p.getCurrency());
        s.put("modules", new java.util.TreeSet<>(p.getModuleCodes()));
        return s;
    }

    private static Map<String, Object> diff(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> d = new LinkedHashMap<>();
        for (String k : after.keySet()) {
            Object b = before.get(k);
            Object a = after.get(k);
            boolean same = b instanceof BigDecimal bb && a instanceof BigDecimal aa ? bb.compareTo(aa) == 0 : java.util.Objects.equals(b, a);
            if (!same) {
                d.put(k, Map.of("from", b == null ? "" : b, "to", a == null ? "" : a));
            }
        }
        return d;
    }

    private static PlanStatus parseStatus(String raw) {
        try {
            return PlanStatus.valueOf(raw == null ? "" : raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "status");
        }
    }

    private static String sortField(String field) {
        return switch (field) {
            case "code", "name", "price", "status", "createdAt" -> field;
            default -> "name";
        };
    }
}
