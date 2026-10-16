package pe.dcs.app.features.config.web;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.config.dto.OrgProfileDtos;
import pe.dcs.app.features.config.service.OrgProfileService;
import pe.dcs.app.features.config.service.OrgGuard;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;

/** M23 · Perfil de la organización. Ver: ORG_ADMIN y ORG_BRANCH_ADMIN. Editar: solo ORG_ADMIN. */
@RestController
@RequestMapping("/api/v1/admin/org-profile")
@RequiredArgsConstructor
public class AdminOrgProfileController {

    private static final String MODULE = "ORG_SETTINGS";

    private final OrgProfileService service;
    private final AccessScopeResolver resolver;
    private final OrgGuard guard;

    private AuthenticatedActor actor() {
        return guard.orgActor(resolver.actor());
    }

    @GetMapping
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<OrgProfileDtos.Response> get() {
        return new ApiResponse<>(200, "ok.common.sent", service.get(actor()));
    }

    @PutMapping
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<OrgProfileDtos.Response> update(@RequestBody OrgProfileDtos.Request req) {
        OrgProfileDtos.Response r = service.update(actor(), req);
        return new ApiResponse<>(200, r.currencyChanged() ? "msg.orgProfile.currencyChange" : "ok.orgProfile.saved", r);
    }
}
