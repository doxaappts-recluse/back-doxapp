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
import pe.dcs.app.features.access.dto.StaffCreateRequest;
import pe.dcs.app.features.access.dto.StaffResponse;
import pe.dcs.app.features.access.dto.StaffRoleRequest;
import pe.dcs.app.features.access.dto.StaffSearchRequest;
import pe.dcs.app.features.access.dto.StaffStatusRequest;
import pe.dcs.app.features.access.dto.StaffUpdateRequest;
import pe.dcs.app.features.access.service.PlatformStaffService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.UUID;

/**
 * M05 · Personal de plataforma. Solo N1: SYSTEM_ADMIN con V C E S; SYSTEM_SUPPORT únicamente V (la autorización la
 * resuelve {@code ModuleAccessInterceptor}, no este controlador).
 */
@RestController
@RequestMapping("/api/v1/platform/staff")
@RequiredArgsConstructor
public class PlatformStaffController {

    private static final String MODULE = "PLATFORM_STAFF";

    private final PlatformStaffService service;
    private final AccessScopeResolver resolver;

    @PostMapping("/search")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<PageResponse<StaffResponse>> search(@RequestBody(required = false) StaffSearchRequest req) {
        return new ApiResponse<>(200, "ok.common.sent", service.search(req, resolver.actor()));
    }

    @GetMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<StaffResponse> get(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.get(id, resolver.actor()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<StaffResponse> create(@Valid @RequestBody StaffCreateRequest req) {
        return new ApiResponse<>(201, "ok.staff.created", service.create(req, resolver.actor()));
    }

    @PutMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<StaffResponse> update(@PathVariable UUID id, @Valid @RequestBody StaffUpdateRequest req) {
        return new ApiResponse<>(200, "ok.staff.updated", service.update(id, req, resolver.actor()));
    }

    @PatchMapping("/{id}/role")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<StaffResponse> changeRole(@PathVariable UUID id, @Valid @RequestBody StaffRoleRequest req) {
        return new ApiResponse<>(200, "ok.staff.roleChanged", service.changeRole(id, req, resolver.actor()));
    }

    @PatchMapping("/{id}/status")
    @ModuleAccess(module = MODULE, action = Action.S)
    public ApiResponse<StaffResponse> changeStatus(@PathVariable UUID id, @Valid @RequestBody StaffStatusRequest req) {
        StaffResponse res = service.changeStatus(id, req, resolver.actor());
        String key = res.status() == pe.dcs.app.features.access.domain.AccessStatus.INACTIVE ? "ok.staff.deactivated" : "ok.staff.activated";
        return new ApiResponse<>(200, key, res);
    }

    @PostMapping("/{id}/invite")
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<Void> resendInvite(@PathVariable UUID id) {
        service.resendInvite(id);
        return new ApiResponse<>(200, "ok.access.inviteResent", null);
    }
}
