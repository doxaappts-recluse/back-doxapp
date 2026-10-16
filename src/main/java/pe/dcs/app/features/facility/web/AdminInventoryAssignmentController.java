package pe.dcs.app.features.facility.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.facility.dto.FacilityDtos;
import pe.dcs.app.features.facility.service.FacilitySupport;
import pe.dcs.app.features.facility.service.InventoryAssignmentService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.Map;
import java.util.UUID;

/** M16 · Asignaciones de inventario (persona, ministerio o espacio). */
@RestController
@RequestMapping("/api/v1/admin/inventory/assignments")
@RequiredArgsConstructor
public class AdminInventoryAssignmentController {

    private static final String MODULE = FacilitySupport.MOD_INVENTORY;

    private final InventoryAssignmentService service;
    private final AccessScopeResolver resolver;

    @PostMapping("/search")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<PageResponse<FacilityDtos.AssignmentView>> search(@RequestBody(required = false) FacilityDtos.AssignmentSearch req) {
        return new ApiResponse<>(200, "ok.common.sent", service.search(resolver.current(), req));
    }

    @GetMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<FacilityDtos.AssignmentView> get(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.get(resolver.current(), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<FacilityDtos.AssignmentView> assign(@RequestBody FacilityDtos.AssignmentRequest req) {
        return new ApiResponse<>(201, "ok.inventory.assigned", service.assign(resolver.actor(), resolver.current(), req));
    }

    @PostMapping("/{id}/return")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<FacilityDtos.AssignmentView> returnItem(@PathVariable UUID id, @RequestBody(required = false) FacilityDtos.AssignmentReturnRequest req) {
        return new ApiResponse<>(200, "ok.inventory.returned", service.returnItem(resolver.actor(), resolver.current(), id, req));
    }

    @PostMapping("/{id}/lost")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<FacilityDtos.AssignmentView> lost(@PathVariable UUID id, @RequestBody(required = false) Map<String, String> body) {
        return new ApiResponse<>(200, "ok.inventory.lost", service.markLost(resolver.actor(), resolver.current(), id, body == null ? null : body.get("note")));
    }
}
