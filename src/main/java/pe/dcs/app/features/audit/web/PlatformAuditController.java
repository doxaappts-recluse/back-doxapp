package pe.dcs.app.features.audit.web;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.audit.dto.*;
import pe.dcs.app.features.audit.service.AuditQueryService;
import pe.dcs.app.features.audit.service.RetentionService;
import pe.dcs.app.features.config.service.OrgGuard;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

/**
 * M22 · auditoría de plataforma (N1): lo que hizo su personal y los eventos sin organización. Solo SYSTEM_ADMIN
 * (SYSTEM_SUPPORT recibe V por rol pero el servicio lo rechaza). No hay edición ni borrado: la auditoría es de solo anexar.
 */
@RestController
@RequestMapping("/api/v1/platform/audit")
@RequiredArgsConstructor
public class PlatformAuditController {

    private static final String MODULE = "AUDIT_LOG";

    private final AuditQueryService service;
    private final RetentionService retention;
    private final AccessScopeResolver resolver;
    private final OrgGuard guard;

    private AuthenticatedActor actor() {
        return guard.staffActor(resolver.actor());
    }

    @PostMapping("/search")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<PageResponse<AuditEventResponse>> search(@RequestBody(required = false) AuditSearchRequest req) {
        return new ApiResponse<>(200, "ok.common.sent", service.searchPlatform(actor(), req));
    }

    @GetMapping("/filters")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<AuditFilterOptions> filters() {
        return new ApiResponse<>(200, "ok.common.sent", service.filtersPlatform(actor()));
    }

    @GetMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<AuditEventResponse> detail(@PathVariable long id) {
        return new ApiResponse<>(200, "ok.common.sent", service.detailPlatform(actor(), id));
    }

    @GetMapping("/retention")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<RetentionView> retention() {
        return new ApiResponse<>(200, "ok.common.sent", retention.viewPlatform(actor()));
    }

    @PutMapping("/retention")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<RetentionView> setRetention(@RequestBody RetentionRequest req) {
        return new ApiResponse<>(200, "ok.retention.saved", retention.setPlatform(actor(), req));
    }

    @PostMapping("/retention/run")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<PurgeResult> run() {
        return new ApiResponse<>(200, "ok.retention.purged", retention.runPlatform(actor()));
    }
}
