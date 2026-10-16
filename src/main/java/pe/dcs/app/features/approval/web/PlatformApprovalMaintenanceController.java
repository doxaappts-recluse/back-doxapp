package pe.dcs.app.features.approval.web;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.dcs.app.features.approval.service.ApprovalMaintenanceService;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;

import java.util.Map;

/** Ejecuta ahora las tareas de M21 (las mismas que corren cada hora) para todas las organizaciones. Solo SYSTEM_ADMIN. */
@RestController
@RequestMapping("/api/v1/platform/approvals/maintenance")
@RequiredArgsConstructor
public class PlatformApprovalMaintenanceController {

    private final ApprovalMaintenanceService service;

    @PostMapping("/run")
    @ModuleAccess(module = "ORG_ADMINS", action = Action.S)
    public ApiResponse<Map<String, Integer>> run() {
        return new ApiResponse<>(200, "ok.approval.maintenanceRun", service.run());
    }
}
