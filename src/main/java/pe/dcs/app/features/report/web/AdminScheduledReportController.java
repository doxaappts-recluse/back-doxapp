package pe.dcs.app.features.report.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.config.service.OrgGuard;
import pe.dcs.app.features.report.dto.ReportDtos;
import pe.dcs.app.features.report.service.ReportSupport;
import pe.dcs.app.features.report.service.ScheduledReportService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;

import java.util.List;
import java.util.UUID;

/**
 * M20 · Programaciones de envío. El spec las reserva a ORG_ADMIN (N2) "salvo delegación" — esta entrega no construye
 * un permiso delegado específico para esto (sería un cuarto nivel de delegación puntual solo para esta pantalla);
 * queda como límite documentado en el "como_ejecutar", igual que otras delegaciones puntuales no cubiertas del proyecto.
 */
@RestController
@RequestMapping("/api/v1/admin/scheduled-reports")
@RequiredArgsConstructor
public class AdminScheduledReportController {

    private static final String MODULE = ReportSupport.MODULE;

    private final ScheduledReportService service;
    private final AccessScopeResolver resolver;
    private final OrgGuard guard;

    @GetMapping
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<List<ReportDtos.ScheduledReportView>> list() {
        return new ApiResponse<>(200, "ok.common.sent", service.list(resolver.current()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<ReportDtos.ScheduledReportView> create(@RequestBody ReportDtos.ScheduledReportRequest req) {
        guard.requireOrgAdmin(resolver.actor());
        return new ApiResponse<>(201, "ok.report.scheduled", service.create(resolver.actor(), resolver.current(), req));
    }

    @PatchMapping("/{id}/status")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<ReportDtos.ScheduledReportView> status(@PathVariable UUID id, @RequestParam boolean active) {
        guard.requireOrgAdmin(resolver.actor());
        return new ApiResponse<>(200, "ok.common.sent", service.setStatus(resolver.current(), id, active));
    }

    @DeleteMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        guard.requireOrgAdmin(resolver.actor());
        service.delete(resolver.current(), id);
        return new ApiResponse<>(200, "ok.report.unscheduled", null);
    }
}
