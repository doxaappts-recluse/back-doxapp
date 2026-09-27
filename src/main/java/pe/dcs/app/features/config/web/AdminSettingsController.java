package pe.dcs.app.features.config.web;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.config.dto.SettingDtos;
import pe.dcs.app.features.config.service.SettingsService;
import pe.dcs.app.features.config.service.OrgGuard;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;

import java.util.List;
import java.util.UUID;

/**
 * M23 · Ajustes por módulo (N2/N3). Ver: ORG_ADMIN y ORG_BRANCH_ADMIN. Escribir a nivel de organización: ORG_ADMIN;
 * a nivel de sede (solo claves con override): ORG_ADMIN o el administrador de esa sede.
 */
@RestController
@RequestMapping("/api/v1/admin/settings")
@RequiredArgsConstructor
public class AdminSettingsController {

    private static final String MODULE = "ORG_SETTINGS";

    private final SettingsService service;
    private final AccessScopeResolver resolver;
    private final OrgGuard guard;

    private AuthenticatedActor actor() {
        return guard.orgActor(resolver.actor());
    }

    @GetMapping
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<List<SettingDtos.NamespaceSummary>> namespaces() {
        return new ApiResponse<>(200, "ok.common.sent", service.namespaces(actor()));
    }

    @GetMapping("/{namespace}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<SettingDtos.View> view(@PathVariable String namespace) {
        return new ApiResponse<>(200, "ok.common.sent", service.view(actor(), resolver.current(), namespace, null));
    }

    @PutMapping("/{namespace}")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<SettingDtos.View> save(@PathVariable String namespace, @RequestBody SettingDtos.SaveRequest req) {
        return new ApiResponse<>(200, "ok.setting.saved", service.save(actor(), resolver.current(), namespace, null, req.values()));
    }

    @DeleteMapping("/{namespace}/{key}")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<SettingDtos.View> reset(@PathVariable String namespace, @PathVariable String key) {
        return new ApiResponse<>(200, "ok.setting.reset", service.reset(actor(), resolver.current(), namespace, null, key));
    }

    @GetMapping("/{namespace}/branch/{branchId}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<SettingDtos.View> viewBranch(@PathVariable String namespace, @PathVariable UUID branchId) {
        return new ApiResponse<>(200, "ok.common.sent", service.view(actor(), resolver.current(), namespace, branchId));
    }

    @PutMapping("/{namespace}/branch/{branchId}")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<SettingDtos.View> saveBranch(@PathVariable String namespace, @PathVariable UUID branchId, @RequestBody SettingDtos.SaveRequest req) {
        return new ApiResponse<>(200, "ok.setting.saved", service.save(actor(), resolver.current(), namespace, branchId, req.values()));
    }

    @DeleteMapping("/{namespace}/branch/{branchId}/{key}")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<SettingDtos.View> resetBranch(@PathVariable String namespace, @PathVariable UUID branchId, @PathVariable String key) {
        return new ApiResponse<>(200, "ok.setting.reset", service.reset(actor(), resolver.current(), namespace, branchId, key));
    }
}
