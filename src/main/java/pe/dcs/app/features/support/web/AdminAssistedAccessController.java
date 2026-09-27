package pe.dcs.app.features.support.web;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.config.service.OrgGuard;
import pe.dcs.app.features.support.dto.AssistedAccessDtos.ApproveRequest;
import pe.dcs.app.features.support.dto.AssistedAccessDtos.DenyRequest;
import pe.dcs.app.features.support.dto.AssistedAccessDtos.GrantResponse;
import pe.dcs.app.features.support.service.AssistedAccessService;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;

import java.util.List;
import java.util.UUID;

/**
 * M22 (D3) · lado de la organización: aprobar/rechazar/revocar el acceso asistido pedido desde uno de sus casos.
 * {@code action = A} (aprobar) — {@code OrgGuard.requireOrgAdmin} dentro del service asegura [V9]: solo ORG_ADMIN,
 * nunca ORG_BRANCH_ADMIN aunque SUPPORT le llegue con la acción A por defecto de módulo (ver V37, BRANCH_ADMIN_CAPS).
 */
@RestController
@RequestMapping("/api/v1/admin/assisted-access")
@RequiredArgsConstructor
public class AdminAssistedAccessController {

    private static final String MODULE = "SUPPORT";

    private final AssistedAccessService service;
    private final AccessScopeResolver resolver;
    private final OrgGuard guard;

    private AuthenticatedActor actor() {
        return guard.orgActor(resolver.actor());
    }

    @GetMapping("/cases/{caseId}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<List<GrantResponse>> listForCase(@PathVariable UUID caseId) {
        return new ApiResponse<>(200, "ok.common.sent", service.listForCase(caseId));
    }

    @PutMapping("/{grantId}/approve")
    @ModuleAccess(module = MODULE, action = Action.A)
    public ApiResponse<GrantResponse> approve(@PathVariable UUID grantId, @RequestBody(required = false) ApproveRequest req) {
        ApproveRequest body = req == null ? new ApproveRequest(null) : req;
        return new ApiResponse<>(200, "ok.assisted.approved", service.approve(actor(), grantId, body));
    }

    @PutMapping("/{grantId}/deny")
    @ModuleAccess(module = MODULE, action = Action.A)
    public ApiResponse<GrantResponse> deny(@PathVariable UUID grantId, @RequestBody DenyRequest req) {
        return new ApiResponse<>(200, "ok.assisted.denied", service.deny(actor(), grantId, req));
    }

    @PutMapping("/{grantId}/revoke")
    @ModuleAccess(module = MODULE, action = Action.A)
    public ApiResponse<GrantResponse> revoke(@PathVariable UUID grantId) {
        return new ApiResponse<>(200, "ok.assisted.revoked", service.revokeByOrg(actor(), grantId));
    }
}
