package pe.dcs.app.features.access.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.dcs.app.features.access.domain.AccessStatus;
import pe.dcs.app.features.access.dto.AccessStatusRequest;
import pe.dcs.app.features.access.dto.OrgAdminEmailRequest;
import pe.dcs.app.features.access.dto.OrgAdminRequest;
import pe.dcs.app.features.access.dto.OrgAdminResponse;
import pe.dcs.app.features.access.service.OrgAdminService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;

import java.util.List;
import java.util.UUID;

/** M05 · Administradores de una organización (ORG_ADMINS, N1). SYSTEM_ADMIN V C E S; SYSTEM_SUPPORT solo V (limitado). */
@RestController
@RequestMapping("/api/v1/platform/organizations/{orgId}/admins")
@RequiredArgsConstructor
public class PlatformOrgAdminController {

    private static final String MODULE = "ORG_ADMINS";

    private final OrgAdminService service;
    private final AccessScopeResolver resolver;

    @GetMapping
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<List<OrgAdminResponse>> list(@PathVariable UUID orgId) {
        return new ApiResponse<>(200, "ok.common.sent", service.list(orgId, resolver.actor()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<OrgAdminResponse> create(@PathVariable UUID orgId, @Valid @RequestBody OrgAdminRequest req) {
        return new ApiResponse<>(201, "ok.access.created", service.create(orgId, req, resolver.actor()));
    }

    @PutMapping("/{accessId}/email")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<OrgAdminResponse> updateEmail(@PathVariable UUID orgId, @PathVariable UUID accessId,
                                                     @Valid @RequestBody OrgAdminEmailRequest req) {
        return new ApiResponse<>(200, "ok.access.updated", service.updateEmail(orgId, accessId, req.email(), resolver.actor()));
    }

    @PatchMapping("/{accessId}/status")
    @ModuleAccess(module = MODULE, action = Action.S)
    public ApiResponse<OrgAdminResponse> changeStatus(@PathVariable UUID orgId, @PathVariable UUID accessId,
                                                      @Valid @RequestBody AccessStatusRequest req) {
        OrgAdminResponse res = service.changeStatus(orgId, accessId, req, resolver.actor());
        return new ApiResponse<>(200, res.status() == AccessStatus.INACTIVE ? "ok.access.deactivated" : "ok.access.activated", res);
    }

    @PostMapping("/{accessId}/invite")
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<Void> resendInvite(@PathVariable UUID orgId, @PathVariable UUID accessId) {
        service.resendInvite(orgId, accessId);
        return new ApiResponse<>(200, "ok.access.inviteResent", null);
    }
}
