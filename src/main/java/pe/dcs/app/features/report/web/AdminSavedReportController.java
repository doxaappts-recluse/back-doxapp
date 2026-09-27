package pe.dcs.app.features.report.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.report.dto.ReportDtos;
import pe.dcs.app.features.report.service.ReportSupport;
import pe.dcs.app.features.report.service.SavedReportService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;

import java.util.List;
import java.util.UUID;

/** M20 · Vistas guardadas: propias de cada usuario [V9-saved]. */
@RestController
@RequestMapping("/api/v1/admin/saved-reports")
@RequiredArgsConstructor
public class AdminSavedReportController {

    private static final String MODULE = ReportSupport.MODULE;

    private final SavedReportService service;
    private final AccessScopeResolver resolver;

    @GetMapping
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<List<ReportDtos.SavedReportView>> list() {
        return new ApiResponse<>(200, "ok.common.sent", service.list(resolver.current()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<ReportDtos.SavedReportView> create(@RequestBody ReportDtos.SavedReportRequest req) {
        return new ApiResponse<>(201, "ok.report.saved", service.create(resolver.current(), req));
    }

    @DeleteMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(resolver.current(), id);
        return new ApiResponse<>(200, "ok.common.sent", null);
    }
}
