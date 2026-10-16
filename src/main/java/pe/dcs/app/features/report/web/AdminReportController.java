package pe.dcs.app.features.report.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.report.dto.ReportDtos;
import pe.dcs.app.features.report.service.ReportCatalogService;
import pe.dcs.app.features.report.service.ReportSupport;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;

import java.util.List;

/** M20 · Catálogo de reportes visibles para esta organización [V1] y su ejecución (N2/N3). */
@RestController
@RequestMapping("/api/v1/admin/reports")
@RequiredArgsConstructor
public class AdminReportController {

    private static final String MODULE = ReportSupport.MODULE;

    private final ReportCatalogService catalog;
    private final AccessScopeResolver resolver;

    /** El módulo REPORTS es el gate de la funcionalidad; la visibilidad de cada reporte además exige que su propio módulo dueño esté contratado. */
    @GetMapping
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<List<ReportDtos.CatalogEntry>> catalog() {
        return new ApiResponse<>(200, "ok.common.sent", catalog.catalog(resolver.actor()));
    }

    @PostMapping("/{code}/run")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<ReportDtos.RunResult> run(@PathVariable String code, @RequestBody(required = false) ReportDtos.RunFilters filters) {
        ReportDtos.RunRequest req = new ReportDtos.RunRequest(code, filters);
        return new ApiResponse<>(200, "ok.common.sent", catalog.run(resolver.actor(), resolver.current(), req));
    }
}
