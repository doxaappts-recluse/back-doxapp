package pe.dcs.app.features.branch.web;

import jakarta.validation.Valid;
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
import pe.dcs.app.features.branch.dto.BranchCreateRequest;
import pe.dcs.app.features.branch.dto.BranchResponse;
import pe.dcs.app.features.branch.dto.BranchSearchRequest;
import pe.dcs.app.features.branch.dto.BranchStatusRequest;
import pe.dcs.app.features.branch.dto.BranchUpdateRequest;
import pe.dcs.app.features.branch.service.BranchService;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.enums.StatusType;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.List;
import java.util.UUID;

/**
 * M04 · Sedes (N1). SYSTEM_ADMIN: V C E S · SYSTEM_SUPPORT: solo V. Inactivar/reactivar y cambiar la principal son
 * gestión de estado (S).
 */
@RestController
@RequestMapping("/api/v1/platform")
@RequiredArgsConstructor
public class PlatformBranchController {

    private static final String MODULE = "BRANCHES";

    private final BranchService service;

    @GetMapping("/organizations/{orgId}/branches")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<List<BranchResponse>> list(@PathVariable UUID orgId) {
        return new ApiResponse<>(200, "ok.common.sent", service.listByOrganization(orgId));
    }

    @PostMapping("/organizations/{orgId}/branches/search")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<PageResponse<BranchResponse>> search(@PathVariable UUID orgId, @RequestBody(required = false) BranchSearchRequest req) {
        return new ApiResponse<>(200, "ok.common.sent", service.search(orgId, req));
    }

    @PostMapping("/organizations/{orgId}/branches")
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<BranchResponse> create(@PathVariable UUID orgId, @Valid @RequestBody BranchCreateRequest req) {
        return new ApiResponse<>(201, "ok.branch.created", service.create(orgId, req));
    }

    @GetMapping("/branches/{id}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<BranchResponse> get(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.getPlatform(id));
    }

    @PutMapping("/branches/{id}")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<BranchResponse> update(@PathVariable UUID id, @Valid @RequestBody BranchUpdateRequest req) {
        return new ApiResponse<>(200, "ok.branch.updated", service.update(id, req));
    }

    @PutMapping("/branches/{id}/status")
    @ModuleAccess(module = MODULE, action = Action.S)
    public ApiResponse<BranchResponse> changeStatus(@PathVariable UUID id, @Valid @RequestBody BranchStatusRequest req) {
        BranchResponse res = service.changeStatus(id, req);
        return new ApiResponse<>(200, res.status() == StatusType.INACTIVE ? "ok.branch.inactivated" : "ok.branch.reactivated", res);
    }

    @PutMapping("/branches/{id}/main")
    @ModuleAccess(module = MODULE, action = Action.S)
    public ApiResponse<BranchResponse> makeMain(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.branch.mainChanged", service.makeMain(id));
    }
}
