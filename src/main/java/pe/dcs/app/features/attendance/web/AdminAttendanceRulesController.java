package pe.dcs.app.features.attendance.web;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.dcs.app.features.attendance.dto.AttendanceDtos;
import pe.dcs.app.features.attendance.service.AttendanceRulesService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;

/** M09 · Reglas de asistencia de la organización (las edita solo el administrador de la organización). */
@RestController
@RequestMapping("/api/v1/admin/attendance-rules")
@RequiredArgsConstructor
public class AdminAttendanceRulesController {

    private static final String MODULE = "ATTENDANCE";

    private final AttendanceRulesService rules;
    private final AccessScopeResolver resolver;

    @GetMapping
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<AttendanceDtos.Rules> get() {
        return new ApiResponse<>(200, "ok.common.sent", rules.get(resolver.current().organizationId()));
    }

    @PutMapping
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<AttendanceDtos.Rules> update(@RequestBody AttendanceDtos.RulesRequest req) {
        return new ApiResponse<>(200, "ok.attendance.rulesSaved", rules.update(resolver.actor(), resolver.current(), req));
    }
}
