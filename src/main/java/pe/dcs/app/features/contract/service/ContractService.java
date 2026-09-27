package pe.dcs.app.features.contract.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.branch.domain.Branch;
import pe.dcs.app.features.branch.domain.BranchRepository;
import pe.dcs.app.features.contract.domain.Contract;
import pe.dcs.app.features.contract.domain.ContractBranchLicense;
import pe.dcs.app.features.contract.domain.ContractBranchLicenseRepository;
import pe.dcs.app.features.contract.domain.ContractRepository;
import pe.dcs.app.features.contract.domain.ContractScope;
import pe.dcs.app.features.contract.domain.ContractStatus;
import pe.dcs.app.features.contract.domain.LicenseDistributionMode;
import pe.dcs.app.features.contract.domain.RenewalType;
import pe.dcs.app.features.contract.dto.AdminContractResponse;
import pe.dcs.app.features.contract.dto.ContractActionRequest;
import pe.dcs.app.features.contract.dto.ContractRequest;
import pe.dcs.app.features.contract.dto.ContractResponse;
import pe.dcs.app.features.contract.dto.BranchOption;
import pe.dcs.app.features.contract.dto.ContractSearchRequest;
import pe.dcs.app.features.contract.dto.NextStartResponse;
import pe.dcs.app.features.module.domain.AppModule;
import pe.dcs.app.features.module.domain.AppModuleRepository;
import pe.dcs.app.features.module.domain.ModuleKind;
import pe.dcs.app.features.organization.domain.Organization;
import pe.dcs.app.features.organization.domain.OrganizationRepository;
import pe.dcs.app.features.organization.domain.OrganizationStatus;
import pe.dcs.app.features.plan.domain.Plan;
import pe.dcs.app.features.plan.domain.PlanRepository;
import pe.dcs.app.features.plan.domain.PlanStatus;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.ContractGate;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.shared.tx.AfterCommit;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PageableUtil;
import pe.dcs.app.util.pagination.PaginationResponse;
import pe.dcs.app.util.pagination.SortRequest;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * M03 · Contratos (N1 gestiona, N2 consulta).
 *
 * <p>Ciclo: PENDING → ACTIVE ⇄ SUSPENDED → EXPIRED (job por endDate) | CANCELLED | REPLACED (versionado).
 * Reglas conservadas: RENEWAL/UPGRADE/DOWNGRADE cierran el actual (REPLACED) y crean uno nuevo enlazado · PENDING se edita
 * siempre in-place · ACTIVE iniciado hoy con upgrade/downgrade hoy → in-place · la corrección administrativa edita in-place
 * en ACTIVE/SUSPENDED · repetir el mismo tipo exige {@code sameTypeTransition} · inicio de RENEWAL = fin anterior + 1,
 * UPGRADE/DOWNGRADE = hoy.
 *
 * <p>Precisión sobre RENEWAL: el inicio es futuro, así que el contrato vigente sigue prestando servicio hasta que el nuevo
 * empiece; en ese momento (activación manual o job) el anterior pasa a REPLACED. Si el inicio ya llegó, el cambio es inmediato.
 *
 * <p>Toda modificación invalida {@link ContractGate} después de confirmar la transacción.
 */
@Service
@RequiredArgsConstructor
public class ContractService {

    static final String MODULE = "CONTRACT";
    static final String ENTITY = "Contract";
    private static final UUID NIL = new UUID(0L, 0L);
    private static final Pattern CURRENCY = Pattern.compile("^[A-Z]{3}$");

    private final ContractRepository contracts;
    private final ContractBranchLicenseRepository licenseRows;
    private final OrganizationRepository organizations;
    private final PlanRepository plans;
    private final AppModuleRepository modules;
    private final BranchRepository branches;
    private final ContractGate gate;
    private final AuditService audit;
    private final Clock clock;

    // ================================================================ consulta (N1)

    @Transactional(readOnly = true)
    public PageResponse<ContractResponse> search(ContractSearchRequest req) {
        ContractSearchRequest.Filters f = req == null || req.filters() == null
                ? new ContractSearchRequest.Filters(null, null, null, null) : req.filters();

        Set<UUID> orgIdsByText = null;
        if (f.q() != null && !f.q().isBlank()) {
            String needle = f.q().trim().toLowerCase();
            orgIdsByText = new HashSet<>();
            for (Organization o : organizations.findAll()) {
                if (o.getName().toLowerCase().contains(needle) || o.getSlug().toLowerCase().contains(needle)) {
                    orgIdsByText.add(o.getId());
                }
            }
        }
        final Set<UUID> textIds = orgIdsByText;
        Specification<Contract> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (textIds != null) {
                ps.add(textIds.isEmpty() ? cb.disjunction() : root.get("organizationId").in(textIds));
            }
            if (f.organizationId() != null) {
                ps.add(cb.equal(root.get("organizationId"), f.organizationId()));
            }
            if (f.status() != null && !f.status().isBlank()) {
                ps.add(cb.equal(root.get("status"), parse(ContractStatus.class, f.status(), "status")));
            }
            if (f.expiringInDays() != null) {
                LocalDate today = LocalDate.now(clock);
                ps.add(cb.equal(root.get("status"), ContractStatus.ACTIVE));
                ps.add(cb.between(root.<LocalDate>get("endDate"), today.minusDays(1), today.plusDays(f.expiringInDays())));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        List<SortRequest> sorts = req == null ? null : req.sorts();
        if (sorts == null || sorts.isEmpty()) {
            SortRequest byCreated = new SortRequest();
            byCreated.setKey("createdAt");
            byCreated.setDirection("DESC");
            sorts = List.of(byCreated);
        }
        Pageable pageable = PageableUtil.buildPageable(req == null ? null : req.pagination(), sorts, ContractService::sortField);
        Page<Contract> page = contracts.findAll(spec, pageable);

        Map<UUID, Organization> orgs = new HashMap<>();
        organizations.findAllById(page.getContent().stream().map(Contract::getOrganizationId).collect(java.util.stream.Collectors.toSet()))
                .forEach(o -> orgs.put(o.getId(), o));
        Map<String, AppModule> catalog = catalog();
        List<ContractResponse> content = page.getContent().stream()
                .map(c -> toResponse(c, orgs.get(c.getOrganizationId()), catalog, false)).toList();
        return new PageResponse<>(content, new PaginationResponse(
                (int) page.getTotalElements(), page.getTotalPages(), page.getSize(), page.getNumber()));
    }

    @Transactional(readOnly = true)
    public ContractResponse get(UUID id) {
        Contract c = find(id);
        return toResponse(c, org(c.getOrganizationId()), catalog(), true);
    }

    /** Fecha de inicio del nuevo periodo para una transición (única fórmula; el front no la replica). */
    @Transactional(readOnly = true)
    public NextStartResponse nextStart(UUID id, String typeRaw) {
        Contract c = find(id);
        RenewalType type = parse(RenewalType.class, typeRaw, "type");
        if (type == RenewalType.NEW) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "type");
        }
        Organization o = org(c.getOrganizationId());
        LocalDate today = today(o);
        LocalDate start = nextStart(c, type, today);
        return new NextStartResponse(type.name(), start, c.getEndDate(), !start.isAfter(today), inPlaceTransition(c, type, today));
    }

    /** Sedes de la organización (solo lectura) para el formulario de contrato. */
    @Transactional(readOnly = true)
    public List<BranchOption> branchesOf(UUID organizationId) {
        org(organizationId);
        return branches.findByOrganizationId(organizationId).stream()
                .sorted(Comparator.comparing(Branch::isMain).reversed().thenComparing(Branch::getName, String.CASE_INSENSITIVE_ORDER))
                .map(b -> new BranchOption(b.getId(), b.getName(), b.getCode(), b.isMain(), b.getStatus().name())).toList();
    }

    // ================================================================ alta y edición

    @Transactional
    public ContractResponse create(ContractRequest req) {
        Organization o = org(required(req.organizationId(), "organizationId"));
        assertOrgOpen(o);
        Contract c = new Contract();
        c.setOrganizationId(o.getId());
        c.setRenewalType(RenewalType.NEW);
        c.setStatus(ContractStatus.PENDING);
        fill(c, o, req, null, Set.of());
        contracts.save(c);
        saveAllocations(c, req);
        record("CREATE", c, snapshot(c));
        return toResponse(c, o, catalog(), true);
    }

    /** Edición de un contrato PENDING (siempre in-place). En ACTIVE/SUSPENDED se exige transición o corrección [V12]. */
    @Transactional
    public ContractResponse update(UUID id, ContractRequest req) {
        Contract c = find(id);
        assertNotTerminal(c);
        if (c.getStatus() != ContractStatus.PENDING) {
            throw new Exceptions("error.contract.needsTransition", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        return editInPlace(c, req, "UPDATE");
    }

    /** Corrección administrativa: edita in-place un contrato PENDING, ACTIVE o SUSPENDED sin declarar transición. */
    @Transactional
    public ContractResponse correct(UUID id, ContractRequest req) {
        Contract c = find(id);
        assertNotTerminal(c);
        return editInPlace(c, req, "CORRECT");
    }

    /** Renovación, upgrade o downgrade (ver reglas en la documentación de la clase). */
    @Transactional
    public ContractResponse transition(UUID id, RenewalType type, ContractRequest req) {
        Contract cur = find(id);
        assertNotTerminal(cur);
        Organization o = org(cur.getOrganizationId());
        assertOrgOpen(o);

        if (cur.getStatus() == ContractStatus.PENDING) {                                   // PENDING: siempre in-place
            if (cur.getPreviousContractId() == null) {
                cur.setRenewalType(type);
            }
            return editInPlace(cur, req, type.name() + "_INPLACE");
        }
        if (cur.getRenewalType() == type && !Boolean.TRUE.equals(req.sameTypeTransition())) {
            throw new Exceptions("error.contract.needsTransition", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        Instant now = clock.instant();
        LocalDate today = today(o);
        Map<String, Object> before = snapshot(cur);

        if (inPlaceTransition(cur, type, today)) {                                         // ACTIVE iniciado hoy + upgrade/downgrade hoy
            checkVersion(cur, req.version());
            fill(cur, o, req, cur.getStartDate(), Set.of(cur.getId()));
            cur.setRenewalType(type);
            contracts.save(cur);
            saveAllocations(cur, req);
            record(type.name() + "_INPLACE", cur, diff(before, snapshot(cur)));
            invalidateGate(cur.getOrganizationId());
            return toResponse(cur, o, catalog(), true);
        }

        checkVersion(cur, req.version());
        LocalDate start = nextStart(cur, type, today);
        Contract next = new Contract();
        next.setOrganizationId(o.getId());
        next.setPreviousContractId(cur.getId());
        next.setRenewalType(type);
        fill(next, o, req, start, Set.of(cur.getId()));
        boolean startsNow = !start.isAfter(today);
        next.setStatus(startsNow ? ContractStatus.ACTIVE : ContractStatus.PENDING);
        if (startsNow) {
            next.setActivatedAt(now);
            replace(cur, now);
        }
        contracts.save(next);
        saveAllocations(next, req);
        Map<String, Object> d = snapshot(next);
        d.put("previousContractId", cur.getId().toString());
        d.put("previousStatusWas", before.get("status"));
        record(type.name(), next, d);
        if (startsNow) {
            record("REPLACED", cur, Map.of("replacedBy", next.getId().toString()));
        }
        invalidateGate(o.getId());
        return toResponse(next, o, catalog(), true);
    }

    // ================================================================ estado

    /** PENDING → ACTIVE y SUSPENDED → ACTIVE. */
    @Transactional
    public ContractResponse activate(UUID id, ContractActionRequest req) {
        Contract c = find(id);
        if (c.getStatus() != ContractStatus.PENDING && c.getStatus() != ContractStatus.SUSPENDED) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, c.getStatus());
        }
        checkVersion(c, req == null ? null : req.version());
        Organization o = org(c.getOrganizationId());
        assertOrgOpen(o);
        if (c.getEndDate().isBefore(today(o))) {
            throw new Exceptions("error.contract.periodEnded", HttpStatus.UNPROCESSABLE_ENTITY, c.getEndDate());
        }
        assertNoOverlap(o.getId(), c.getScope(), c.getBranchId(), c.getStartDate(), c.getEndDate(), excluded(c));
        Instant now = clock.instant();
        boolean reactivation = c.getStatus() == ContractStatus.SUSPENDED;
        if (c.getPreviousContractId() != null) {
            contracts.findById(c.getPreviousContractId())
                    .filter(p -> p.getStatus() == ContractStatus.ACTIVE || p.getStatus() == ContractStatus.SUSPENDED)
                    .ifPresent(p -> {
                        replace(p, now);
                        record("REPLACED", p, Map.of("replacedBy", c.getId().toString()));
                    });
        }
        c.setStatus(ContractStatus.ACTIVE);
        c.setStatusReason(null);
        c.setSuspendedAt(null);
        if (c.getActivatedAt() == null) {
            c.setActivatedAt(now);
        }
        contracts.save(c);
        record(reactivation ? "REACTIVATE" : "ACTIVATE", c, Map.of("status", "ACTIVE"));
        invalidateGate(o.getId());
        return toResponse(c, o, catalog(), true);
    }

    @Transactional
    public ContractResponse suspend(UUID id, ContractActionRequest req) {
        Contract c = find(id);
        if (c.getStatus() != ContractStatus.ACTIVE) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, c.getStatus());
        }
        checkVersion(c, req == null ? null : req.version());
        String reason = reason(req);
        c.setStatus(ContractStatus.SUSPENDED);
        c.setStatusReason(reason);
        c.setSuspendedAt(clock.instant());
        contracts.save(c);
        record("SUSPEND", c, Map.of("reason", reason));
        invalidateGate(c.getOrganizationId());
        return toResponse(c, org(c.getOrganizationId()), catalog(), true);
    }

    @Transactional
    public ContractResponse cancel(UUID id, ContractActionRequest req) {
        Contract c = find(id);
        if (c.isTerminal()) {
            throw new Exceptions("error.contract.terminal", HttpStatus.UNPROCESSABLE_ENTITY, c.getStatus());
        }
        checkVersion(c, req == null ? null : req.version());
        String reason = reason(req);
        c.setStatus(ContractStatus.CANCELLED);
        c.setStatusReason(reason);
        c.setCancelledAt(clock.instant());
        contracts.save(c);
        record("CANCEL", c, Map.of("reason", reason));
        invalidateGate(c.getOrganizationId());
        return toResponse(c, org(c.getOrganizationId()), catalog(), true);
    }

    // ================================================================ N2

    /** Contrato de la organización del actor (solo lectura). */
    @Transactional(readOnly = true)
    public AdminContractResponse mine(AuthenticatedActor actor) {
        if (actor.organizationId() == null) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
        Organization o = org(actor.organizationId());
        List<Contract> all = contracts.findByOrganizationIdOrderByStartDateDescCreatedAtDesc(o.getId());
        Contract current = pick(all, ContractStatus.ACTIVE);
        boolean limited = current == null;
        if (current == null) {
            current = pick(all, ContractStatus.SUSPENDED);
        }
        if (current == null) {
            current = pick(all, ContractStatus.PENDING);
        }
        if (current == null && !all.isEmpty()) {
            current = all.get(0);
        }
        List<ContractResponse.HistoryItem> history = all.stream().map(x -> historyItem(x, false)).toList();
        if (current == null) {
            return new AdminContractResponse(null, true, false, null, history);
        }
        ContractResponse res = toResponse(current, o, catalog(), true);
        boolean soon = current.getStatus() == ContractStatus.ACTIVE && res.daysToExpire() != null && res.daysToExpire() <= 30;
        return new AdminContractResponse(res, limited, soon, res.daysToExpire(), history);
    }

    private static Contract pick(List<Contract> all, ContractStatus status) {
        return all.stream().filter(c -> c.getStatus() == status).findFirst().orElse(null);
    }

    // ================================================================ reglas de negocio

    private ContractResponse editInPlace(Contract c, ContractRequest req, String action) {
        Organization o = org(c.getOrganizationId());
        assertOrgOpen(o);
        checkVersion(c, req.version());
        Map<String, Object> before = snapshot(c);
        // Un PENDING que nació de una renovación conserva su inicio (fin anterior + 1).
        LocalDate forcedStart = c.getStatus() == ContractStatus.PENDING && c.getPreviousContractId() != null ? c.getStartDate() : null;
        fill(c, o, req, forcedStart, excluded(c));
        contracts.save(c);
        saveAllocations(c, req);
        record(action, c, diff(before, snapshot(c)));
        invalidateGate(o.getId());
        return toResponse(c, o, catalog(), true);
    }

    /**
     * Valida y aplica los términos: [V4] obligatorios · [V5] fechas · [V6] solapamiento · [V7] sede · [V8] reparto ·
     * [V9] licencias en uso · [V10] sedes activas · módulos vigentes. {@code forcedStart} != null = el servidor fija el inicio.
     */
    private void fill(Contract c, Organization o, ContractRequest r, LocalDate forcedStart, Set<UUID> overlapExcluded) {
        // [V4]
        UUID planId = required(r.planId(), "planId");
        BigDecimal price = required(r.price(), "price");
        String currency = required(r.currency(), "currency").trim().toUpperCase();
        Integer maxLicenses = required(r.maxLicenses(), "maxLicenses");
        LocalDate start = forcedStart != null ? forcedStart : required(r.startDate(), "startDate");
        LocalDate end = required(r.endDate(), "endDate");
        if (price.compareTo(BigDecimal.ZERO) < 0 || price.scale() > 2) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "price");
        }
        if (!CURRENCY.matcher(currency).matches()) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "currency");
        }
        if (maxLicenses <= 0) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "maxLicenses");
        }
        if (r.maxBranches() != null && r.maxBranches() < 1) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "maxBranches");
        }

        // [V5]
        if (!end.isAfter(start)) {
            if (forcedStart != null) {
                throw new Exceptions("error.contract.endBeforeNewStart", HttpStatus.UNPROCESSABLE_ENTITY, start);
            }
            throw new Exceptions("error.contract.datesInvalid", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        Plan plan = plans.findById(planId).orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
        boolean planChanged = !planId.equals(c.getPlanId());
        if (planChanged) {
            if (plan.getStatus() == PlanStatus.RETIRED) {
                throw new Exceptions("error.plan.retired", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            if (plan.getStatus() == PlanStatus.DRAFT) {
                throw new Exceptions("error.plan.notPublished", HttpStatus.UNPROCESSABLE_ENTITY);
            }
        }

        // [V7]
        ContractScope scope = r.scope() == null ? ContractScope.ORGANIZATION : r.scope();
        UUID branchId = null;
        if (scope == ContractScope.BRANCH) {
            if (r.branchId() == null) {
                throw new Exceptions("error.contract.branchRequired", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            Branch b = branches.findById(r.branchId()).orElse(null);
            if (b == null || !b.getOrganizationId().equals(o.getId())) {
                throw new Exceptions("error.contract.branchInvalid", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            branchId = b.getId();
        }
        LicenseDistributionMode dist = scope == ContractScope.BRANCH || r.distributionMode() == null
                ? LicenseDistributionMode.SHARED : r.distributionMode();
        Integer maxBranches = scope == ContractScope.BRANCH ? null : r.maxBranches();

        // [V8]
        validateAllocations(o, scope, dist, maxLicenses, r.allocations());

        // [V9]
        long used = scope == ContractScope.BRANCH
                ? contracts.licensesUsedInBranch(o.getId(), branchId) : contracts.licensesUsedInOrganization(o.getId());
        if (maxLicenses < used) {
            throw new Exceptions("error.contract.licensesInUse", HttpStatus.UNPROCESSABLE_ENTITY, used);
        }
        // [V10]
        if (maxBranches != null) {
            long active = contracts.activeBranchCount(o.getId());
            if (maxBranches < active) {
                throw new Exceptions("error.contract.branchesInUse", HttpStatus.UNPROCESSABLE_ENTITY, active);
            }
        }

        Set<String> moduleCodes = resolveModules(c, plan, r.modules());
        assertNoOverlap(o.getId(), scope, branchId, start, end, overlapExcluded);            // [V6]

        c.setPlanId(planId);
        if (planChanged || c.getPlanName() == null) {
            c.setPlanName(plan.getName());                                                  // copia: el plan puede cambiar después
        }
        c.setPrice(price);
        c.setCurrency(currency);
        c.setStartDate(start);
        c.setEndDate(end);
        c.setMaxLicenses(maxLicenses);
        c.setMaxBranches(maxBranches);
        c.setScope(scope);
        c.setBranchId(branchId);
        c.setDistributionMode(dist);
        c.getModuleCodes().clear();
        c.getModuleCodes().addAll(moduleCodes);
    }

    private void validateAllocations(Organization o, ContractScope scope, LicenseDistributionMode dist, int max,
                                     List<ContractRequest.Allocation> allocs) {
        if (allocs == null || allocs.isEmpty()) {
            return;
        }
        if (scope != ContractScope.ORGANIZATION || dist != LicenseDistributionMode.ALLOCATED) {
            throw new Exceptions("error.contract.allocationOnlyOrg", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        Set<UUID> seen = new HashSet<>();
        long sum = 0;
        for (ContractRequest.Allocation a : allocs) {
            if (a.branchId() == null || a.allocatedLicenses() == null || a.allocatedLicenses() < 0) {
                throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "allocations");
            }
            if (!seen.add(a.branchId())) {
                throw new Exceptions("error.contract.allocationDuplicateBranch", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            Branch b = branches.findById(a.branchId()).orElse(null);
            if (b == null || !b.getOrganizationId().equals(o.getId())) {
                throw new Exceptions("error.contract.branchInvalid", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            sum += a.allocatedLicenses();
        }
        if (sum > max) {
            throw new Exceptions("error.contract.allocationExceedsMax", HttpStatus.UNPROCESSABLE_ENTITY, sum, max);
        }
    }

    /** Módulos del contrato: los enviados o, si no hay, los del plan. Deben ser CONTRACTABLE y publicados (salvo los que ya tenía). */
    private Set<String> resolveModules(Contract c, Plan plan, List<String> requested) {
        Set<String> codes = new LinkedHashSet<>();
        if (requested == null || requested.isEmpty()) {
            codes.addAll(plan.getModuleCodes());
        } else {
            requested.forEach(x -> {
                if (x != null && !x.isBlank()) {
                    codes.add(x.trim().toUpperCase());
                }
            });
        }
        if (codes.isEmpty()) {
            throw new Exceptions("error.contract.modulesEmpty", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        for (String code : codes) {
            AppModule m = modules.findById(code).orElse(null);
            boolean already = c.getModuleCodes().contains(code);
            if (m == null || m.getKind() != ModuleKind.CONTRACTABLE || (!m.isPublished() && !already)) {
                throw new Exceptions("error.contract.moduleInvalid", HttpStatus.UNPROCESSABLE_ENTITY, code);
            }
        }
        return codes;
    }

    private void assertNoOverlap(UUID orgId, ContractScope scope, UUID branchId, LocalDate start, LocalDate end, Set<UUID> excluded) {
        Set<UUID> ex = excluded.isEmpty() ? Set.of(NIL) : excluded;
        if (contracts.countOverlapping(orgId, scope.name(), branchId, start, end, ex) > 0) {
            throw new Exceptions("error.contract.overlap", HttpStatus.CONFLICT);
        }
    }

    private static Set<UUID> excluded(Contract c) {
        Set<UUID> ex = new HashSet<>();
        if (c.getId() != null) {
            ex.add(c.getId());
        }
        if (c.getPreviousContractId() != null) {
            ex.add(c.getPreviousContractId());
        }
        return ex;
    }

    private void replace(Contract c, Instant now) {
        c.setStatus(ContractStatus.REPLACED);
        c.setReplacedAt(now);
        contracts.save(c);
    }

    /** Inicio del nuevo periodo: RENEWAL = fin anterior + 1 · UPGRADE/DOWNGRADE = hoy. */
    LocalDate nextStart(Contract cur, RenewalType type, LocalDate today) {
        return type == RenewalType.RENEWAL ? cur.getEndDate().plusDays(1) : today;
    }

    boolean inPlaceTransition(Contract cur, RenewalType type, LocalDate today) {
        return cur.getStatus() == ContractStatus.ACTIVE && cur.getStartDate().equals(today)
                && (type == RenewalType.UPGRADE || type == RenewalType.DOWNGRADE);
    }

    private void assertNotTerminal(Contract c) {                                             // [V11]
        if (c.isTerminal()) {
            throw new Exceptions("error.contract.terminal", HttpStatus.UNPROCESSABLE_ENTITY, c.getStatus());
        }
    }

    private void assertOrgOpen(Organization o) {
        if (o.getStatus() == OrganizationStatus.CLOSED) {
            throw new Exceptions("error.contract.orgClosed", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private static void checkVersion(Contract c, Long version) {
        if (version != null && !version.equals(c.getVersion())) {
            throw new Exceptions("error.common.concurrentUpdate", HttpStatus.CONFLICT);
        }
    }

    private static String reason(ContractActionRequest req) {
        String r = req == null || req.reason() == null ? "" : req.reason().trim();
        if (r.isEmpty()) {
            throw new Exceptions("error.common.reasonRequired", HttpStatus.BAD_REQUEST);
        }
        return r;
    }

    private void saveAllocations(Contract c, ContractRequest req) {
        licenseRows.deleteByContractId(c.getId());
        if (req.allocations() == null || c.getDistributionMode() != LicenseDistributionMode.ALLOCATED) {
            return;
        }
        for (ContractRequest.Allocation a : req.allocations()) {
            ContractBranchLicense row = new ContractBranchLicense();
            row.setContractId(c.getId());
            row.setBranchId(a.branchId());
            row.setAllocatedLicenses(a.allocatedLicenses());
            licenseRows.save(row);
        }
    }

    private void invalidateGate(UUID orgId) {
        AfterCommit.run(() -> gate.invalidate(orgId));
    }

    // ================================================================ utilidades

    private Contract find(UUID id) {
        return contracts.findById(id).orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    private Organization org(UUID id) {
        return organizations.findById(id).orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    LocalDate today(Organization o) {
        return LocalDate.now(clock.withZone(ZoneId.of(o.getTimezone())));
    }

    private Map<String, AppModule> catalog() {
        Map<String, AppModule> m = new HashMap<>();
        modules.findAll().forEach(x -> m.put(x.getCode(), x));
        return m;
    }

    private static <T> T required(T value, String field) {
        if (value == null || (value instanceof String s && s.isBlank())) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, field);
        }
        return value;
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, String field) {
        try {
            return Enum.valueOf(type, value == null ? "" : value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, field);
        }
    }

    private void record(String action, Contract c, Map<String, Object> diff) {
        audit.record(new AuditService.Command(MODULE, action, ENTITY, c.getId(), c.getOrganizationId(), c.getBranchId(), diff));
    }

    static Map<String, Object> snapshot(Contract c) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("status", c.getStatus().name());
        s.put("planId", c.getPlanId() == null ? null : c.getPlanId().toString());
        s.put("planName", c.getPlanName());
        s.put("price", c.getPrice() == null ? null : c.getPrice().toPlainString());
        s.put("currency", c.getCurrency());
        s.put("startDate", String.valueOf(c.getStartDate()));
        s.put("endDate", String.valueOf(c.getEndDate()));
        s.put("maxLicenses", c.getMaxLicenses());
        s.put("maxBranches", c.getMaxBranches());
        s.put("scope", c.getScope().name());
        s.put("distributionMode", c.getDistributionMode().name());
        s.put("renewalType", c.getRenewalType().name());
        s.put("modules", new TreeSet<>(c.getModuleCodes()).toString());
        return s;
    }

    static Map<String, Object> diff(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> d = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : after.entrySet()) {
            Object b = before.get(e.getKey());
            if (!java.util.Objects.equals(b, e.getValue())) {
                Map<String, Object> ch = new LinkedHashMap<>();
                ch.put("from", b == null ? "" : b);
                ch.put("to", e.getValue() == null ? "" : e.getValue());
                d.put(e.getKey(), ch);
            }
        }
        return d;
    }

    private static String sortField(String field) {
        return switch (field) {
            case "startDate", "endDate", "status", "createdAt", "price", "maxLicenses", "planName", "renewalType" -> field;
            default -> "createdAt";
        };
    }

    // ================================================================ mapeo

    static List<String> actionsFor(ContractStatus s) {
        return switch (s) {
            case PENDING -> List.of("edit", "activate", "cancel");
            case ACTIVE -> List.of("suspend", "cancel", "renew", "upgrade", "downgrade", "correct");
            case SUSPENDED -> List.of("activate", "cancel", "renew", "upgrade", "downgrade", "correct");
            default -> List.of();
        };
    }

    private ContractResponse toResponse(Contract c, Organization o, Map<String, AppModule> catalog, boolean detail) {
        List<ContractResponse.ModuleRef> mods = c.getModuleCodes().stream().sorted().map(code -> {
            AppModule m = catalog.get(code);
            return new ContractResponse.ModuleRef(code, m == null ? code : m.getNameEs(), m == null ? code : m.getNameEn());
        }).toList();

        Long days = null;
        if (o != null && (c.getStatus() == ContractStatus.ACTIVE || c.getStatus() == ContractStatus.SUSPENDED)) {
            days = ChronoUnit.DAYS.between(today(o), c.getEndDate());
        }

        String branchName = null;
        if (c.getBranchId() != null) {
            branchName = branches.findById(c.getBranchId()).map(Branch::getName).orElse(null);
        }

        List<ContractResponse.AllocationItem> allocs = null;
        ContractResponse.Licenses lic = null;
        Long branchesUsed = null;
        List<ContractResponse.HistoryItem> history = null;
        if (detail) {
            UUID orgId = c.getOrganizationId();
            Map<UUID, Long> usedByBranch = new HashMap<>();
            for (Object[] row : contracts.licensesUsedByBranch(orgId)) {
                usedByBranch.put(UUID.fromString((String) row[0]), ((Number) row[1]).longValue());
            }
            Map<UUID, String> names = new HashMap<>();
            branches.findByOrganizationId(orgId).forEach(b -> names.put(b.getId(), b.getName()));
            allocs = licenseRows.findByContractId(c.getId()).stream()
                    .sorted(Comparator.comparing(a -> names.getOrDefault(a.getBranchId(), "")))
                    .map(a -> new ContractResponse.AllocationItem(a.getBranchId(), names.get(a.getBranchId()),
                            a.getAllocatedLicenses(), usedByBranch.getOrDefault(a.getBranchId(), 0L))).toList();
            long used = c.getScope() == ContractScope.BRANCH
                    ? contracts.licensesUsedInBranch(orgId, c.getBranchId()) : contracts.licensesUsedInOrganization(orgId);
            lic = new ContractResponse.Licenses(c.getMaxLicenses(), used, Math.max(0, c.getMaxLicenses() - used));
            branchesUsed = contracts.activeBranchCount(orgId);
            history = history(c);
        }
        return new ContractResponse(c.getId(), c.getOrganizationId(), o == null ? null : o.getName(), o == null ? null : o.getSlug(),
                c.getPlanId(), c.getPlanName(), c.getPrice(), c.getCurrency(), c.getStartDate(), c.getEndDate(),
                c.getMaxLicenses(), c.getMaxBranches(), c.getDistributionMode(), c.getScope(), c.getBranchId(), branchName,
                c.getStatus(), c.getStatusReason(), c.getRenewalType(), c.getPreviousContractId(), c.getActivatedAt(),
                c.getSuspendedAt(), c.getCancelledAt(), c.getReplacedAt(), c.getExpiredAt(), mods, allocs, lic, branchesUsed,
                days, actionsFor(c.getStatus()), history, c.getCreatedAt(), c.getUpdatedAt(), c.getVersion());
    }

    /** Cadena de versiones que contiene al contrato (anteriores y siguientes), de la más antigua a la más reciente. */
    private List<ContractResponse.HistoryItem> history(Contract c) {
        List<Contract> chain = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        Contract back = c;
        while (back != null && seen.add(back.getId())) {
            chain.add(0, back);
            back = back.getPreviousContractId() == null ? null : contracts.findById(back.getPreviousContractId()).orElse(null);
        }
        Contract fwd = c;
        while (fwd != null) {
            List<Contract> next = contracts.findByPreviousContractId(fwd.getId());
            fwd = next.isEmpty() ? null : next.get(0);
            if (fwd != null && seen.add(fwd.getId())) {
                chain.add(fwd);
            } else {
                fwd = null;
            }
        }
        return chain.stream().map(x -> historyItem(x, x.getId().equals(c.getId()))).toList();
    }

    private ContractResponse.HistoryItem historyItem(Contract x, boolean current) {
        return new ContractResponse.HistoryItem(x.getId(), x.getStatus(), x.getRenewalType(), x.getPlanName(), x.getPrice(),
                x.getCurrency(), x.getStartDate(), x.getEndDate(), x.getMaxLicenses(), x.getActivatedAt(), x.getReplacedAt(), current);
    }
}
