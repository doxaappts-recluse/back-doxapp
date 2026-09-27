package pe.dcs.app.features.visibility.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.dcs.app.features.approval.dto.ApprovalDtos;
import pe.dcs.app.features.visibility.dto.VisibilityDtos;
import pe.dcs.app.features.visibility.service.DataAccessRuleService;
import pe.dcs.app.features.visibility.service.VisibilityGrantService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.List;
import java.util.UUID;

/** M21 · Reglas de visibilidad entre sedes y autorizaciones vigentes (VISIBILITY_RULES, contratable): V consultar, C pedir acceso, E editar reglas, A revocar. */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminVisibilityController {

    private static final String MODULE = "VISIBILITY_RULES";

    private final DataAccessRuleService rules;
    private final VisibilityGrantService grants;
    private final AccessScopeResolver resolver;

    @GetMapping("/data-access-rules")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<List<VisibilityDtos.Rule>> rules() {
        return new ApiResponse<>(200, "ok.common.sent", rules.list(resolver.current()));
    }

    @PutMapping("/data-access-rules/{module}")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<VisibilityDtos.Rule> saveRule(@PathVariable String module, @RequestBody VisibilityDtos.RuleRequest req) {
        return new ApiResponse<>(200, "ok.rule.saved", rules.save(resolver.actor(), resolver.current(), module.toUpperCase(), req));
    }

    @PostMapping("/visibility-grants/search")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<PageResponse<VisibilityDtos.Grant>> search(@RequestBody(required = false) VisibilityDtos.Search req) {
        return new ApiResponse<>(200, "ok.common.sent", grants.search(resolver.actor(), resolver.current(), req));
    }

    @PostMapping("/visibility-grants/request")
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<ApprovalDtos.Summary> request(@RequestBody VisibilityDtos.AccessRequest req) {
        return new ApiResponse<>(201, "ok.visibility.requested", grants.request(resolver.actor(), resolver.current(), req));
    }

    @PostMapping("/visibility-grants/{id}/revoke")
    @ModuleAccess(module = MODULE, action = Action.A)
    public ApiResponse<VisibilityDtos.Grant> revoke(@PathVariable UUID id, @RequestBody(required = false) VisibilityDtos.RevokeRequest req) {
        return new ApiResponse<>(200, "ok.visibility.revoked", grants.revoke(resolver.actor(), resolver.current(), id, req));
    }
}
