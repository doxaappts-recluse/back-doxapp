package pe.dcs.app.features.module.web;

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
import pe.dcs.app.features.module.dto.ModuleRequest;
import pe.dcs.app.features.module.dto.ModuleResponse;
import pe.dcs.app.features.module.dto.ModuleStatusRequest;
import pe.dcs.app.features.module.service.ModuleCatalogService;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;

import java.util.List;

/** M03 · Catálogo de módulos (N1). SYSTEM_ADMIN: V C E S · SYSTEM_SUPPORT: V. */
@RestController
@RequestMapping("/api/v1/platform/modules")
@RequiredArgsConstructor
public class PlatformModuleController {

    private static final String MODULE = "MODULE_CATALOG";

    private final ModuleCatalogService service;

    @GetMapping
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<List<ModuleResponse>> list() {
        return new ApiResponse<>(200, "ok.common.sent", service.list());
    }

    @GetMapping("/{code}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<ModuleResponse> get(@PathVariable String code) {
        return new ApiResponse<>(200, "ok.common.sent", service.get(code));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<ModuleResponse> create(@Valid @RequestBody ModuleRequest req) {
        return new ApiResponse<>(201, "ok.module.created", service.create(req));
    }

    @PutMapping("/{code}")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<ModuleResponse> update(@PathVariable String code, @Valid @RequestBody ModuleRequest req) {
        return new ApiResponse<>(200, "ok.module.updated", service.update(code, req));
    }

    @PutMapping("/{code}/status")
    @ModuleAccess(module = MODULE, action = Action.S)
    public ApiResponse<ModuleResponse> changeStatus(@PathVariable String code, @Valid @RequestBody ModuleStatusRequest req) {
        ModuleResponse res = service.changeStatus(code, req.status());
        return new ApiResponse<>(200, "RETIRED".equals(res.status()) ? "ok.module.retired" : "ok.module.published", res);
    }
}
