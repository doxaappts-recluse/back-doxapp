package pe.dcs.app.features.volunteer.web;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.dcs.app.features.volunteer.service.VolunteerMaintenanceService;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;

import java.util.Map;

/** Ejecuta ahora la tarea de recordatorios de M11b (la misma que corre cada 15 minutos) para todas las organizaciones. Solo SYSTEM_ADMIN. */
@RestController
@RequestMapping("/api/v1/platform/volunteers/maintenance")
@RequiredArgsConstructor
public class PlatformVolunteerMaintenanceController {

    private final VolunteerMaintenanceService service;

    @PostMapping("/run")
    @ModuleAccess(module = "ORG_ADMINS", action = Action.S)
    public ApiResponse<Map<String, Integer>> run() {
        return new ApiResponse<>(200, "ok.shift.maintenanceRun", service.run());
    }
}
