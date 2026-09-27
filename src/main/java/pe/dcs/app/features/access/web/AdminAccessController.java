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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.dcs.app.features.access.domain.AccessStatus;
import pe.dcs.app.features.access.dto.AccessCreateRequest;
import pe.dcs.app.features.access.dto.AccessResponse;
import pe.dcs.app.features.access.dto.AccessSearchRequest;
import pe.dcs.app.features.access.dto.AccessStatusRequest;
import pe.dcs.app.features.access.dto.AccessUpdateRequest;
import pe.dcs.app.features.access.dto.DelegableModule;
import pe.dcs.app.features.access.dto.PersonCandidate;
import pe.dcs.app.features.access.service.AccessService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.List;
import java.util.UUID;

/**
 * M05 · Equipo y accesos (SUPPORT_TEAM, N2/N3). ORG_ADMIN gestiona a todo el equipo; ORG_BRANCH_ADMIN, los ORG_USER de
 * su sede (lo aplica {@link AccessService}: el módulo solo dice quién puede llamar al endpoint).
 */
@RestController
@RequestMapping("/api/v1/admin/accesses")
@RequiredArgsConstructor
public class AdminAccessController {

    private static final String MODULE = "SUPPORT_TEAM";

    private final AccessService service;
    private final AccessScopeResolver resolver;

    @PostMapping("/search")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<PageResponse<AccessResponse>> search(@RequestBody(required = false) AccessSearchRequest req) {
        return new ApiResponse<>(200, "ok.common.sent", service.search(req, resolver.actor(), resolver.current()));
    }

    @GetMapping("/candidates")
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<List<PersonCandidate>> candidates(@RequestParam(required = false) String q) {
        return new ApiResponse<>(200, "ok.common.sent", service.candidates(q, resolver.current()));
    }

    @GetMapping("/delegable-modules")
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<List<DelegableModule>> delegableModules() {
        resolver.current();
        return new ApiResponse<>(200, "ok.common.sent", service.delegableModules(resolver.actor()));
    }

    @GetMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<AccessResponse> get(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.get(id, resolver.actor(), resolver.current()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<AccessResponse> create(@Valid @RequestBody AccessCreateRequest req) {
        return new ApiResponse<>(201, "ok.access.created", service.create(req, resolver.actor(), resolver.current()));
    }

    @PutMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<AccessResponse> update(@PathVariable UUID id, @Valid @RequestBody AccessUpdateRequest req) {
        return new ApiResponse<>(200, "ok.access.updated", service.update(id, req, resolver.actor(), resolver.current()));
    }

    @PatchMapping("/{id}/status")
    @ModuleAccess(module = MODULE, action = Action.S)
    public ApiResponse<AccessResponse> changeStatus(@PathVariable UUID id, @Valid @RequestBody AccessStatusRequest req) {
        AccessResponse res = service.changeStatus(id, req, resolver.actor(), resolver.current());
        String key = res.status() == AccessStatus.INACTIVE ? "ok.access.deactivated" : "ok.access.activated";
        return new ApiResponse<>(200, key, res);
    }

    @PostMapping("/{id}/invite")
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<Void> resendInvite(@PathVariable UUID id) {
        service.resendInvite(id, resolver.actor(), resolver.current());
        return new ApiResponse<>(200, "ok.access.inviteResent", null);
    }
}
