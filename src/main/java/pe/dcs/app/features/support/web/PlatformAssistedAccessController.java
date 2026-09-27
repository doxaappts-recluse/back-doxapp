package pe.dcs.app.features.support.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.config.service.OrgGuard;
import pe.dcs.app.features.support.dto.AssistedAccessDtos.EnterResponse;
import pe.dcs.app.features.support.dto.AssistedAccessDtos.GrantResponse;
import pe.dcs.app.features.support.dto.AssistedAccessDtos.RequestAccess;
import pe.dcs.app.features.support.service.AssistedAccessService;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;

import java.util.List;
import java.util.UUID;

/**
 * M22 (D3) · lado del personal de plataforma: pedir acceso asistido desde un caso abierto, entrar mientras esté
 * ACTIVE, y terminarlo antes de que expire. Aprobar/rechazar/revocar es siempre del lado de la organización
 * (ver {@link AdminAssistedAccessController}) — nunca autoaprobado por el staff (V2 del spec).
 */
@RestController
@RequestMapping("/api/v1/platform/support/assisted-access")
@RequiredArgsConstructor
public class PlatformAssistedAccessController {

    private static final String MODULE = "SUPPORT";

    private final AssistedAccessService service;
    private final AccessScopeResolver resolver;
    private final OrgGuard guard;

    private AuthenticatedActor actor() {
        return guard.staffActor(resolver.actor());
    }

    @PostMapping("/cases/{caseId}/request")
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<GrantResponse> request(@PathVariable UUID caseId, @RequestBody RequestAccess req) {
        return new ApiResponse<>(201, "ok.assisted.requested", service.request(actor(), caseId, req));
    }

    @GetMapping("/cases/{caseId}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<List<GrantResponse>> listForCase(@PathVariable UUID caseId) {
        return new ApiResponse<>(200, "ok.common.sent", service.listForCase(caseId));
    }

    @PostMapping("/{grantId}/enter")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<EnterResponse> enter(@PathVariable UUID grantId) {
        return new ApiResponse<>(200, "ok.common.sent", service.enter(actor(), grantId));
    }

    @PostMapping("/{grantId}/end")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<GrantResponse> end(@PathVariable UUID grantId) {
        return new ApiResponse<>(200, "ok.assisted.ended", service.endByStaff(actor(), grantId));
    }
}
