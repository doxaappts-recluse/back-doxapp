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
import pe.dcs.app.features.access.dto.AccessStatusRequest;
import pe.dcs.app.features.access.dto.DelegableModule;
import pe.dcs.app.features.access.service.DelegationRules;
import pe.dcs.app.features.access.dto.ProfileRequest;
import pe.dcs.app.features.access.dto.ProfileResponse;
import pe.dcs.app.features.access.dto.ProfileSearchRequest;
import pe.dcs.app.features.access.service.PermissionProfileService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.enums.StatusType;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.List;
import java.util.UUID;

/** M05 · Perfiles de permisos (PERMISSION_PROFILES, N2). */
@RestController
@RequestMapping("/api/v1/admin/permission-profiles")
@RequiredArgsConstructor
public class AdminPermissionProfileController {

    private static final String MODULE = "PERMISSION_PROFILES";

    private final PermissionProfileService service;
    private final AccessScopeResolver resolver;
    private final DelegationRules rules;

    @PostMapping("/search")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<PageResponse<ProfileResponse>> search(@RequestBody(required = false) ProfileSearchRequest req) {
        return new ApiResponse<>(200, "ok.common.sent", service.search(req, resolver.current()));
    }

    /** Módulos y acciones que quien arma el perfil puede incluir (los mismos que puede delegar). */
    @GetMapping("/delegable-modules")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<List<DelegableModule>> delegableModules() {
        return new ApiResponse<>(200, "ok.common.sent", rules.delegable(resolver.actor()));
    }

    @GetMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<ProfileResponse> get(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.get(id, resolver.current()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<ProfileResponse> create(@Valid @RequestBody ProfileRequest req) {
        return new ApiResponse<>(201, "ok.profile.saved", service.create(req, resolver.actor(), resolver.current()));
    }

    @PutMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<ProfileResponse> update(@PathVariable UUID id, @Valid @RequestBody ProfileRequest req) {
        return new ApiResponse<>(200, "ok.profile.saved", service.update(id, req, resolver.actor(), resolver.current()));
    }

    @PatchMapping("/{id}/status")
    @ModuleAccess(module = MODULE, action = Action.S)
    public ApiResponse<ProfileResponse> changeStatus(@PathVariable UUID id, @Valid @RequestBody AccessStatusRequest req) {
        if (req.status() != pe.dcs.app.features.access.domain.AccessStatus.ACTIVE
                && req.status() != pe.dcs.app.features.access.domain.AccessStatus.INACTIVE) {
            throw new pe.dcs.app.util.Exceptions("error.common.invalidState", HttpStatus.CONFLICT, req.status());
        }
        StatusType to = req.status() == pe.dcs.app.features.access.domain.AccessStatus.ACTIVE ? StatusType.ACTIVE : StatusType.INACTIVE;
        return new ApiResponse<>(200, to == StatusType.ACTIVE ? "ok.profile.activated" : "ok.profile.deactivated",
                service.changeStatus(id, to, resolver.current()));
    }
}
