package pe.dcs.app.features.facility.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.facility.dto.FacilityDtos;
import pe.dcs.app.features.facility.service.FacilitySupport;
import pe.dcs.app.features.facility.service.InventoryMovementService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;

import java.util.List;
import java.util.UUID;

/** M16 · Movimientos de inventario (entradas/salidas). */
@RestController
@RequestMapping("/api/v1/admin/inventory/movements")
@RequiredArgsConstructor
public class AdminInventoryMovementController {

    private static final String MODULE = FacilitySupport.MOD_INVENTORY;

    private final InventoryMovementService service;
    private final AccessScopeResolver resolver;

    @GetMapping
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<List<FacilityDtos.MovementView>> byItem(@RequestParam UUID itemId) {
        return new ApiResponse<>(200, "ok.common.sent", service.byItem(resolver.current(), itemId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<FacilityDtos.MovementView> record(@RequestBody FacilityDtos.MovementRequest req) {
        return new ApiResponse<>(201, "ok.inventory.movementRecorded", service.record(resolver.actor(), resolver.current(), req));
    }
}
