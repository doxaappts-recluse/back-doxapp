package pe.dcs.app.features.report.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.report.dto.ReportDtos;
import pe.dcs.app.features.report.spi.PlatformReportProvider;
import pe.dcs.app.features.report.spi.ReportProvider;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.security.authz.AuthorizationService;
import pe.dcs.app.util.Exceptions;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * M20 · Catálogo de reportes: recoge todos los beans {@link ReportProvider}/{@link PlatformReportProvider} ya
 * registrados por cada módulo (Spring los inyecta, ninguno se referencia por nombre) y aplica las reglas comunes
 * (contrato+permiso [V1], alcance de sede [V3][V10], columnas sensibles [V6]) sin tocar la consulta de cada uno.
 */
@Service
@RequiredArgsConstructor
public class ReportCatalogService {

    private final List<ReportProvider> providers;
    private final List<PlatformReportProvider> platformProviders;
    private final AuthorizationService authz;
    private final Clock clock;

    private ReportProvider find(String code) {
        return providers.stream().filter(p -> p.code().equals(code)).findFirst()
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    private PlatformReportProvider findPlatform(String code) {
        return platformProviders.stream().filter(p -> p.code().equals(code)).findFirst()
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    /** Reportes visibles para este actor de organización: módulo contratado + acción V [V1]. */
    @Transactional(readOnly = true)
    public List<ReportDtos.CatalogEntry> catalog(AuthenticatedActor actor) {
        List<ReportDtos.CatalogEntry> out = new ArrayList<>();
        for (ReportProvider p : providers) {
            Set<String> actions = authz.effectiveActions(actor, p.moduleCode());
            if (actions.contains("V")) {
                out.add(new ReportDtos.CatalogEntry(p.code(), p.moduleCode(), p.nameKey(), p.chartType(), p.columns(), p.sensitiveColumns(), actions.contains("X")));
            }
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<ReportDtos.CatalogEntry> platformCatalog() {
        return platformProviders.stream().map(p -> new ReportDtos.CatalogEntry(p.code(), ReportSupport.PLATFORM_MODULE, p.nameKey(), p.chartType(), p.columns(), List.of(), true)).toList();
    }

    /** [V9] misma consulta que el módulo dueño; [V6] enmascara columnas sensibles sin H. */
    @Transactional(readOnly = true)
    public ReportDtos.RunResult run(AuthenticatedActor actor, AccessScope scope, ReportDtos.RunRequest req) {
        ReportProvider p = find(req.code());
        Set<String> actions = authz.effectiveActions(actor, p.moduleCode());
        if (!actions.contains("V")) {
            throw new Exceptions("error.report.notAvailable", HttpStatus.FORBIDDEN);                                                     // [V1]
        }
        ReportDtos.RunFilters filters = validateScope(scope, ReportSupport.normalize(req.filters(), clock));
        ReportProvider.ReportResult r = p.run(scope, toProviderFilters(filters));
        return mask(req.code(), r, p.sensitiveColumns(), actions.contains("H"));
    }

    @Transactional(readOnly = true)
    public ReportDtos.RunResult runPlatform(String code, ReportDtos.RunFilters filtersIn) {
        PlatformReportProvider p = findPlatform(code);
        ReportDtos.RunFilters filters = ReportSupport.normalize(filtersIn, clock);
        ReportProvider.ReportResult r = p.run(toProviderFilters(filters));
        return new ReportDtos.RunResult(code, r.columns(), r.rows(), r.summary(), List.of());
    }

    /** [V3][V10] cualquier sede fuera del alcance del actor → 404 (no se distingue de "no existe" para no filtrar información). */
    ReportDtos.RunFilters validateScope(AccessScope scope, ReportDtos.RunFilters f) {
        if (scope.allBranches() || f.branchIds() == null || f.branchIds().isEmpty()) {
            return f;
        }
        for (UUID b : f.branchIds()) {
            if (!scope.canSeeBranch(b)) {
                throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);                                                     // [V10]
            }
        }
        return f;
    }

    List<ReportProvider> providers() {
        return providers;
    }

    ReportProvider provider(String code) {
        return find(code);
    }

    PlatformReportProvider platformProvider(String code) {
        return findPlatform(code);
    }

    AuthorizationService authz() {
        return authz;
    }

    private ReportProvider.Filters toProviderFilters(ReportDtos.RunFilters f) {
        return new ReportProvider.Filters(f.branchIds(), f.from(), f.to(), Map.of());
    }

    private ReportDtos.RunResult mask(String code, ReportProvider.ReportResult r, List<String> sensitive, boolean hasH) {
        if (sensitive.isEmpty() || hasH) {
            return new ReportDtos.RunResult(code, r.columns(), r.rows(), r.summary(), List.of());
        }
        List<Integer> maskIdx = new ArrayList<>();
        for (int i = 0; i < r.columns().size(); i++) {
            if (sensitive.contains(r.columns().get(i))) {
                maskIdx.add(i);
            }
        }
        List<List<Object>> masked = new ArrayList<>();
        for (List<Object> row : r.rows()) {
            List<Object> copy = new ArrayList<>(row);
            for (int idx : maskIdx) {
                copy.set(idx, null);
            }
            masked.add(copy);
        }
        Map<String, Object> summary = new LinkedHashMap<>(r.summary());
        for (String s : sensitive) {
            summary.remove(s);
        }
        return new ReportDtos.RunResult(code, r.columns(), masked, summary, sensitive);                                                  // [V6]
    }
}
