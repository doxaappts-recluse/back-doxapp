package pe.dcs.app.features.facility.web;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.facility.dto.FacilityDtos;
import pe.dcs.app.features.facility.service.FacilitySupport;
import pe.dcs.app.features.facility.service.SpaceService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;

/** M16 · Reglas de espacios de la organización (duración máxima, quién puede reservar, recurrencia máxima). */
@RestController
@RequestMapping("/api/v1/admin/space-rules")
@RequiredArgsConstructor
public class AdminSpaceRulesController {

    private static final String MODULE = FacilitySupport.MOD_SPACES;

    private final SpaceService service;
    private final AccessScopeResolver resolver;

    @GetMapping
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<FacilityDtos.SpaceRulesView> get() {
        return new ApiResponse<>(200, "ok.common.sent", service.rules(resolver.current().organizationId()));
    }

    @PutMapping
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<FacilityDtos.SpaceRulesView> update(@RequestBody FacilityDtos.SpaceRulesRequest req) {
        return new ApiResponse<>(200, "ok.common.updated", service.updateRules(resolver.actor(), resolver.current(), req));
    }
}
