package pe.dcs.app.features.config.web;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.config.dto.SettingDtos;
import pe.dcs.app.features.config.service.PlatformSettingService;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;

import java.util.List;

/** M23 · Ajustes de plataforma (N1). SYSTEM_ADMIN: V E · SYSTEM_SUPPORT: solo V. Cambiar exige motivo y confirmación [V3]. */
@RestController
@RequestMapping("/api/v1/platform/settings")
@RequiredArgsConstructor
public class PlatformSettingsController {

    private static final String MODULE = "PLATFORM_SETTINGS";

    private final PlatformSettingService service;

    @GetMapping
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<List<SettingDtos.PlatformItem>> list() {
        return new ApiResponse<>(200, "ok.common.sent", service.list());
    }

    @PutMapping("/{namespace}/{key}")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<SettingDtos.PlatformItem> save(@PathVariable String namespace, @PathVariable String key,
                                                      @RequestBody SettingDtos.PlatformSaveRequest req) {
        return new ApiResponse<>(200, "ok.setting.saved", service.save(namespace, key, req));
    }

    @DeleteMapping("/{namespace}/{key}")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<SettingDtos.PlatformItem> reset(@PathVariable String namespace, @PathVariable String key,
                                                       @RequestParam(required = false) String reason,
                                                       @RequestParam(required = false) Boolean confirm) {
        return new ApiResponse<>(200, "ok.setting.reset", service.reset(namespace, key, reason, confirm));
    }
}
