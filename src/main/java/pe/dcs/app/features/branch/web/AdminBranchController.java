package pe.dcs.app.features.branch.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.multipart.MultipartFile;
import pe.dcs.app.features.branch.dto.BranchResponse;
import pe.dcs.app.features.branch.dto.BranchSelfUpdateRequest;
import pe.dcs.app.features.branch.service.BranchService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;

import java.util.List;
import java.util.UUID;

/**
 * M04 · Sedes de la organización (N2/N3). ORG_ADMIN ve todas; ORG_BRANCH_ADMIN y ORG_USER (delegado) solo las suyas.
 * Se edita únicamente la presentación (nombre visible, logo, contacto, dirección, horarios); no se crean sedes ni se
 * cambian código, principal o estado (403).
 */
@RestController
@RequestMapping("/api/v1/admin/branches")
@RequiredArgsConstructor
public class AdminBranchController {

    private static final String MODULE = "BRANCH";

    private final BranchService service;
    private final AccessScopeResolver resolver;

    @GetMapping
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<List<BranchResponse>> list() {
        return new ApiResponse<>(200, "ok.common.sent", service.mine(resolver.actor(), resolver.current()));
    }

    @GetMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<BranchResponse> get(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.getMine(resolver.actor(), resolver.current(), id));
    }

    @PutMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<BranchResponse> update(@PathVariable UUID id, @Valid @RequestBody BranchSelfUpdateRequest req) {
        return new ApiResponse<>(200, "ok.branch.updated", service.updateMine(resolver.actor(), resolver.current(), id, req));
    }

    @PostMapping(value = "/{id}/logo", consumes = "multipart/form-data")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<BranchResponse> uploadLogo(@PathVariable UUID id, @RequestPart("file") MultipartFile file) {
        return new ApiResponse<>(200, "ok.branch.updated", service.uploadLogo(resolver.actor(), resolver.current(), id, file));
    }

    @DeleteMapping("/{id}/logo")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<BranchResponse> deleteLogo(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.branch.updated", service.deleteLogo(resolver.actor(), resolver.current(), id));
    }
}
