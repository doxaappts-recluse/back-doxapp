package pe.dcs.app.features.attendance.web;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.dcs.app.features.attendance.service.AttendanceMaintenanceService;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;

import java.util.Map;

/** Ejecuta ahora las tareas de M09 (las mismas que corren cada hora) para todas las organizaciones. Solo SYSTEM_ADMIN. */
@RestController
@RequestMapping("/api/v1/platform/attendance/maintenance")
@RequiredArgsConstructor
public class PlatformAttendanceMaintenanceController {

    private final AttendanceMaintenanceService service;

    @PostMapping("/run")
    @ModuleAccess(module = "ORG_ADMINS", action = Action.S)
    public ApiResponse<Map<String, Integer>> run() {
        return new ApiResponse<>(200, "ok.attendance.maintenanceRun", service.run());
    }
}
