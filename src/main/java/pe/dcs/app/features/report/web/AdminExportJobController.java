package pe.dcs.app.features.report.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.report.dto.ReportDtos;
import pe.dcs.app.features.report.service.ExportJobService;
import pe.dcs.app.features.report.service.ReportSupport;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.shared.storage.FileStorageService;
import pe.dcs.app.util.ApiResponse;

import java.util.List;
import java.util.UUID;

/** M20 · Trabajos de exportación: generar, listar y descargar (ventana de 24h [V8]). */
@RestController
@RequestMapping("/api/v1/admin/export-jobs")
@RequiredArgsConstructor
public class AdminExportJobController {

    private static final String MODULE = ReportSupport.MODULE;

    private final ExportJobService service;
    private final AccessScopeResolver resolver;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.X)
    public ApiResponse<UUID> export(@RequestBody ReportDtos.ExportRequest req) {
        return new ApiResponse<>(201, "ok.report.exported", service.exportOrg(resolver.actor(), resolver.current(), req));
    }

    @GetMapping
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<List<ReportDtos.ExportJobView>> list() {
        return new ApiResponse<>(200, "ok.common.sent", service.list(resolver.current()));
    }

    @GetMapping("/{id}/download")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ResponseEntity<byte[]> download(@PathVariable UUID id) {
        FileStorageService.StoredFile f = service.download(resolver.current(), id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(f.contentType())).body(f.data());
    }
}
