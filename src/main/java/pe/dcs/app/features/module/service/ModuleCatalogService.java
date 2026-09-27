package pe.dcs.app.features.module.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.contract.domain.ContractRepository;
import pe.dcs.app.features.module.domain.AppModule;
import pe.dcs.app.features.module.domain.AppModuleRepository;
import pe.dcs.app.features.module.domain.ModuleKind;
import pe.dcs.app.features.module.domain.ModuleLevel;
import pe.dcs.app.features.module.dto.ModuleRequest;
import pe.dcs.app.features.module.dto.ModuleResponse;
import pe.dcs.app.features.plan.domain.PlanRepository;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleCatalog;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.shared.tx.AfterCommit;
import pe.dcs.app.util.Exceptions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * M03 · Catálogo de módulos (N1). Nombres, niveles, acciones y jerarquía viven solo aquí; el front lee {@code /me/menu}.
 * [V1] código con formato y único · [V2] un módulo hijo no declara niveles que el padre no tiene ·
 * retirar solo si ningún contrato ACTIVE lo incluye.
 */
@Service
@RequiredArgsConstructor
public class ModuleCatalogService {

    static final String MODULE = "MODULE_CATALOG";
    static final String ENTITY = "Module";
    private static final Pattern CODE = Pattern.compile("^[A-Z0-9_]{3,40}$");
    private static final Pattern ROUTE = Pattern.compile("^[a-z0-9][a-z0-9\\-/]{0,119}$");
    private static final Set<String> ACTION_CODES = new LinkedHashSet<>();

    static {
        for (Action a : Action.values()) {
            ACTION_CODES.add(a.name());
        }
    }

    private final AppModuleRepository modules;
    private final ContractRepository contracts;
    private final PlanRepository plans;
    private final ModuleCatalog catalog;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public List<ModuleResponse> list() {
        return modules.findAll().stream()
                .sorted(Comparator.comparingInt(AppModule::getSortOrder).thenComparing(AppModule::getCode))
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ModuleResponse get(String code) {
        return toResponse(find(code));
    }

    @Transactional
    public ModuleResponse create(ModuleRequest req) {
        String code = req.code() == null ? "" : req.code().trim();
        if (!CODE.matcher(code).matches()) {                                              // [V1]
            throw new Exceptions("error.module.codeFormat", HttpStatus.BAD_REQUEST);
        }
        if (modules.existsById(code)) {
            throw new Exceptions("error.module.codeTaken", HttpStatus.CONFLICT);
        }
        AppModule m = new AppModule();
        m.setCode(code);
        m.setStatus("DRAFT");
        apply(m, req, true);
        modules.save(m);
        refreshCatalog();
        audit.record(AuditService.Command.of(MODULE, "CREATE", ENTITY, code, Map.of("code", code, "kind", m.getKind().name(),
                "levels", m.levelList(), "actions", m.actionList())));
        return toResponse(m);
    }

    @Transactional
    public ModuleResponse update(String code, ModuleRequest req) {
        AppModule m = find(code);
        Map<String, Object> before = snapshot(m);
        apply(m, req, false);
        modules.save(m);
        refreshCatalog();
        audit.record(AuditService.Command.of(MODULE, "UPDATE", ENTITY, code, diff(before, snapshot(m))));
        return toResponse(m);
    }

    /** DRAFT → PUBLISHED · PUBLISHED → RETIRED · RETIRED → PUBLISHED. */
    @Transactional
    public ModuleResponse changeStatus(String code, String target) {
        AppModule m = find(code);
        String from = m.getStatus();
        String to = target == null ? "" : target.trim().toUpperCase();
        if (!allowedStatuses(m).contains(to)) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, from);
        }
        if (to.equals("RETIRED")) {
            if (m.getKind() == ModuleKind.BASE) {
                throw new Exceptions("error.module.baseRetire", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            if (contracts.countActiveWithModule(code) > 0) {
                throw new Exceptions("error.module.inUse", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            boolean publishedChild = modules.findAll().stream()
                    .anyMatch(c -> code.equals(c.getParentCode()) && c.isPublished());
            if (publishedChild) {
                throw new Exceptions("error.module.hasChildren", HttpStatus.UNPROCESSABLE_ENTITY);
            }
        }
        m.setStatus(to);
        modules.save(m);
        refreshCatalog();
        audit.record(AuditService.Command.of(MODULE, "STATUS", ENTITY, code, Map.of("from", from, "to", to)));
        return toResponse(m);
    }

    // ---------------------------------------------------------------- reglas

    private void apply(AppModule m, ModuleRequest req, boolean creating) {
        List<String> levels = normalizeLevels(req.levels());
        List<String> actions = normalizeActions(req.actions());
        ModuleKind kind = parseKind(req.kind());
        String parent = req.parentCode() == null || req.parentCode().isBlank() ? null : req.parentCode().trim();

        if (parent != null) {
            AppModule p = modules.findById(parent).orElseThrow(() -> new Exceptions("error.module.parentNotFound", HttpStatus.UNPROCESSABLE_ENTITY));
            if (parent.equals(m.getCode()) || isDescendant(p, m.getCode())) {
                throw new Exceptions("error.module.parentCycle", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            if (!p.levelList().containsAll(levels)) {                                      // [V2]
                throw new Exceptions("error.module.levelsOutOfParent", HttpStatus.UNPROCESSABLE_ENTITY, String.join(", ", p.levelList()));
            }
        }
        if (!creating) {
            // Los hijos actuales no pueden quedar con niveles que este módulo ya no declara.
            for (AppModule child : modules.findAll()) {
                if (m.getCode().equals(child.getParentCode()) && !levels.containsAll(child.levelList())) {
                    throw new Exceptions("error.module.levelsOutOfChild", HttpStatus.UNPROCESSABLE_ENTITY, child.getCode());
                }
            }
            if (m.getKind() != kind && (contracts.countReferencing(m.getCode()) > 0 || plans.countByModule(m.getCode()) > 0)) {
                throw new Exceptions("error.module.kindInUse", HttpStatus.UNPROCESSABLE_ENTITY);
            }
        }
        String route = req.route() == null || req.route().isBlank() ? null : req.route().trim();
        if (route != null && !ROUTE.matcher(route).matches()) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "route");
        }

        m.setNameEs(req.nameEs().trim());
        m.setNameEn(req.nameEn().trim());
        m.setLevels(levels.toArray(new String[0]));
        m.setParentCode(parent);
        m.setKind(kind);
        m.setActions(actions.toArray(new String[0]));
        m.setDelegable(req.delegable() == null || req.delegable());
        m.setRoute(route);
        m.setIcon(req.icon() == null || req.icon().isBlank() ? null : req.icon().trim());
        m.setSortOrder(req.sortOrder() == null ? 500 : req.sortOrder());
    }

    private boolean isDescendant(AppModule candidate, String ancestorCode) {
        AppModule cur = candidate;
        int guard = 0;
        while (cur != null && cur.getParentCode() != null && guard++ < 20) {
            if (cur.getParentCode().equals(ancestorCode)) {
                return true;
            }
            cur = modules.findById(cur.getParentCode()).orElse(null);
        }
        return false;
    }

    private static List<String> normalizeLevels(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "levels");
        }
        Set<String> valid = new LinkedHashSet<>();
        for (ModuleLevel l : ModuleLevel.values()) {
            valid.add(l.name());
        }
        List<String> out = new ArrayList<>();
        for (String l : raw) {
            String v = l == null ? "" : l.trim().toUpperCase();
            if (!valid.contains(v)) {
                throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "levels");
            }
            if (!out.contains(v)) {
                out.add(v);
            }
        }
        out.sort(Comparator.naturalOrder());
        return out;
    }

    private static List<String> normalizeActions(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "actions");
        }
        List<String> out = new ArrayList<>();
        for (String a : raw) {
            String v = a == null ? "" : a.trim().toUpperCase();
            if (!ACTION_CODES.contains(v)) {
                throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "actions");
            }
            if (!out.contains(v)) {
                out.add(v);
            }
        }
        if (!out.contains("V")) {
            throw new Exceptions("error.module.actionsNeedView", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        // Orden canónico: el de la enumeración Action.
        List<String> canonical = new ArrayList<>();
        for (Action a : Action.values()) {
            if (out.contains(a.name())) {
                canonical.add(a.name());
            }
        }
        return canonical;
    }

    private static ModuleKind parseKind(String raw) {
        try {
            return ModuleKind.valueOf(raw == null ? "" : raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "kind");
        }
    }

    // ---------------------------------------------------------------- utilidades

    private AppModule find(String code) {
        return modules.findById(code == null ? "" : code.trim().toUpperCase())
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    private void refreshCatalog() {
        AfterCommit.run(catalog::invalidate);
    }

    private List<String> allowedStatuses(AppModule m) {
        return switch (m.getStatus()) {
            case "DRAFT", "RETIRED" -> List.of("PUBLISHED");
            case "PUBLISHED" -> List.of("RETIRED");
            default -> List.of();
        };
    }

    private ModuleResponse toResponse(AppModule m) {
        return new ModuleResponse(m.getCode(), m.getNameEs(), m.getNameEn(), m.levelList(), m.getParentCode(),
                m.getKind().name(), m.actionList(), m.isDelegable(), m.getRoute(), m.getIcon(), m.getSortOrder(), m.getStatus(),
                contracts.countActiveWithModule(m.getCode()), plans.countByModule(m.getCode()), allowedStatuses(m));
    }

    private static Map<String, Object> snapshot(AppModule m) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("nameEs", m.getNameEs());
        s.put("nameEn", m.getNameEn());
        s.put("levels", m.levelList());
        s.put("parentCode", m.getParentCode());
        s.put("kind", m.getKind().name());
        s.put("actions", m.actionList());
        s.put("delegable", m.isDelegable());
        s.put("route", m.getRoute());
        s.put("icon", m.getIcon());
        s.put("sortOrder", m.getSortOrder());
        return s;
    }

    private static Map<String, Object> diff(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> d = new LinkedHashMap<>();
        for (String k : after.keySet()) {
            if (!java.util.Objects.equals(before.get(k), after.get(k))) {
                d.put(k, Map.of("from", before.get(k) == null ? "" : before.get(k), "to", after.get(k) == null ? "" : after.get(k)));
            }
        }
        return d;
    }
}
