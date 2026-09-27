package pe.dcs.app.features.rite.web;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.dcs.app.features.rite.dto.RiteDtos;
import pe.dcs.app.features.rite.service.FaithTimelineService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;

import java.util.UUID;

/** M08 · Línea de vida eclesial de una persona (pestaña de la ficha). Cada bloque solo se llena con el permiso V de su módulo. */
@RestController
@RequestMapping("/api/v1/admin/faith")
@ModuleAccess(module = "MEMBERSHIP", action = Action.V)
@RequiredArgsConstructor
public class AdminFaithController {

    private final FaithTimelineService timeline;
    private final AccessScopeResolver resolver;

    @GetMapping("/persons/{id}")
    public ApiResponse<RiteDtos.Timeline> person(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", timeline.forPerson(resolver.actor(), resolver.current(), id));
    }
}
