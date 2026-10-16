package pe.dcs.app.features.access.web;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.dcs.app.features.access.service.AccessExpiryService;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;

import java.util.Map;

/** Ejecuta ahora la tarea de vigencia de accesos (la misma que corre cada hora). Solo quien gestiona administradores (S). */
@RestController
@RequestMapping("/api/v1/platform/accesses/maintenance")
@RequiredArgsConstructor
public class PlatformAccessMaintenanceController {

    private final AccessExpiryService expiry;

    @PostMapping("/run")
    @ModuleAccess(module = "ORG_ADMINS", action = Action.S)
    public ApiResponse<Map<String, Integer>> run() {
        return new ApiResponse<>(200, "ok.access.maintenanceRun", Map.of("expired", expiry.run()));
    }
}
