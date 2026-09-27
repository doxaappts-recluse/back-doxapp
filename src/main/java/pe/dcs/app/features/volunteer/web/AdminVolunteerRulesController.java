package pe.dcs.app.features.volunteer.web;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.dcs.app.features.volunteer.dto.VolunteerDtos;
import pe.dcs.app.features.volunteer.service.VolunteerRulesService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;

/** M11b · Reglas de voluntariado de la organización. */
@RestController
@RequestMapping("/api/v1/admin/volunteer-rules")
@ModuleAccess(module = "VOLUNTEER_SCHEDULING", action = Action.V)
@RequiredArgsConstructor
public class AdminVolunteerRulesController {

    private final VolunteerRulesService service;
    private final AccessScopeResolver resolver;

    @GetMapping
    public ApiResponse<VolunteerDtos.RulesResponse> get() {
        return new ApiResponse<>(200, "ok.common.sent", service.get(resolver.current().organizationId()));
    }

    @PutMapping
    public ApiResponse<VolunteerDtos.RulesResponse> update(@RequestBody VolunteerDtos.RulesRequest req) {
        return new ApiResponse<>(200, "ok.shift.rulesSaved", service.update(resolver.actor(), resolver.current(), req));
    }
}
