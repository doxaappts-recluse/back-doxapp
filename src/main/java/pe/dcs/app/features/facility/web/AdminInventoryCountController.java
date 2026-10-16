package pe.dcs.app.features.facility.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.facility.dto.FacilityDtos;
import pe.dcs.app.features.facility.service.FacilitySupport;
import pe.dcs.app.features.facility.service.InventoryCountService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;

import java.util.UUID;

/** M16 · Conteo físico de inventario: sesión DRAFT → cargar lo contado → cerrar (genera ajustes auditados). */
@RestController
@RequestMapping("/api/v1/admin/inventory/counts")
@RequiredArgsConstructor
public class AdminInventoryCountController {

    private static final String MODULE = FacilitySupport.MOD_INVENTORY;

    private final InventoryCountService service;
    private final AccessScopeResolver resolver;

    @GetMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<FacilityDtos.CountView> get(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.get(resolver.current(), id));
    }

    @PostMapping("/start")
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<FacilityDtos.CountView> start(@RequestBody FacilityDtos.CountStartRequest req) {
        return new ApiResponse<>(201, "ok.common.created", service.start(resolver.actor(), resolver.current(), req));
    }

    @PutMapping("/{id}/enter")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<FacilityDtos.CountView> enter(@PathVariable UUID id, @RequestBody FacilityDtos.CountEnterRequest req) {
        return new ApiResponse<>(200, "ok.common.updated", service.enter(resolver.actor(), resolver.current(), id, req));
    }

    @PostMapping("/{id}/close")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<FacilityDtos.CountView> close(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.inventory.countClosed", service.close(resolver.actor(), resolver.current(), id));
    }
}
