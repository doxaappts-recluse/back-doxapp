package pe.dcs.app.features.pastoral.web;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.dcs.app.features.pastoral.service.PastoralMaintenanceService;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;

import java.util.Map;

/** Ejecuta ahora la tarea de M12 (avisos y escalamiento de SLA pastoral) para todas las organizaciones. Solo SYSTEM_ADMIN. */
@RestController
@RequestMapping("/api/v1/platform/pastoral/maintenance")
@RequiredArgsConstructor
public class PlatformPastoralMaintenanceController {

    private final PastoralMaintenanceService service;

    @PostMapping("/run")
    @ModuleAccess(module = "ORG_ADMINS", action = Action.S)
    public ApiResponse<Map<String, Integer>> run() {
        return new ApiResponse<>(200, "ok.pastoral.maintenanceRun", service.run());
    }
}
