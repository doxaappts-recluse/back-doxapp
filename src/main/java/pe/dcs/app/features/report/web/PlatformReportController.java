package pe.dcs.app.features.report.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.config.service.OrgGuard;
import pe.dcs.app.features.report.dto.ReportDtos;
import pe.dcs.app.features.report.service.ExportJobService;
import pe.dcs.app.features.report.service.ReportCatalogService;
import pe.dcs.app.features.report.service.ReportSupport;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.shared.storage.FileStorageService;
import pe.dcs.app.util.ApiResponse;

import java.util.List;
import java.util.UUID;

/** M20 · Reportes de plataforma (N1) [V1]: solo agregados de contratos/uso, nunca datos de una organización. */
@RestController
@RequestMapping("/api/v1/platform/reports")
@RequiredArgsConstructor
public class PlatformReportController {

    private static final String MODULE = ReportSupport.PLATFORM_MODULE;

    private final ReportCatalogService catalog;
    private final ExportJobService exportJobs;
    private final AccessScopeResolver resolver;
    private final OrgGuard guard;

    private void staff() {
        guard.staffActor(resolver.actor());
    }

    @GetMapping
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<List<ReportDtos.CatalogEntry>> catalog() {
        staff();
        return new ApiResponse<>(200, "ok.common.sent", catalog.platformCatalog());
    }

    @PostMapping("/{code}/run")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<ReportDtos.RunResult> run(@PathVariable String code, @RequestBody(required = false) ReportDtos.RunFilters filters) {
        staff();
        return new ApiResponse<>(200, "ok.common.sent", catalog.runPlatform(code, filters));
    }

    @PostMapping("/export-jobs")
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.X)
    public ApiResponse<UUID> export(@RequestBody ReportDtos.ExportRequest req) {
        staff();
        return new ApiResponse<>(201, "ok.report.exported", exportJobs.exportPlatform(resolver.actor(), req));
    }

    @GetMapping("/export-jobs")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<List<ReportDtos.ExportJobView>> exportJobs() {
        staff();
        return new ApiResponse<>(200, "ok.common.sent", exportJobs.listPlatform());
    }

    @GetMapping("/export-jobs/{id}/download")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ResponseEntity<byte[]> download(@PathVariable UUID id) {
        staff();
        FileStorageService.StoredFile f = exportJobs.downloadPlatform(id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(f.contentType())).body(f.data());
    }
}
