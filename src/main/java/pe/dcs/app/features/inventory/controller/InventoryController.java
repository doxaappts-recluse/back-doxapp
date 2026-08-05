package pe.dcs.app.features.inventory.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.inventory.request.InventoryAssignmentFormRequest;
import pe.dcs.app.features.inventory.request.InventoryAssignmentReturnRequest;
import pe.dcs.app.features.inventory.request.InventoryAssignmentSearchRequest;
import pe.dcs.app.features.inventory.request.InventoryItemFormRequest;
import pe.dcs.app.features.inventory.request.InventoryItemSearchRequest;
import pe.dcs.app.features.inventory.request.InventoryMovementFormRequest;
import pe.dcs.app.features.inventory.request.InventoryMovementSearchRequest;
import pe.dcs.app.features.inventory.response.InventoryAssignmentResponse;
import pe.dcs.app.features.inventory.response.InventoryItemResponse;
import pe.dcs.app.features.inventory.response.InventoryMinistryOptionResponse;
import pe.dcs.app.features.inventory.response.InventoryMovementResponse;
import pe.dcs.app.features.inventory.response.InventoryPersonSearchResponse;
import pe.dcs.app.features.inventory.service.InventoryService;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.List;
import java.util.UUID;

/**
 * Inventario: catálogo de ítems por sede (no delegable) + movimientos
 * de stock y asignaciones de custodia (delegables). SYSTEM no tiene
 * acceso — ver InventoryAccessGuard.
 */
@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService service;

    @GetMapping("/find-by-dni")
    public ApiResponse<InventoryPersonSearchResponse> findPersonByDni(@RequestParam String dni) {
        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.personaEncontradaCorrectamente",
                service.findPersonByDni(dni)
        );
    }

    @GetMapping("/ministries")
    public ApiResponse<List<InventoryMinistryOptionResponse>> listMinistries() {
        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.ministeriosObtenidosCorrectamente",
                service.listMinistries()
        );
    }

    // =====================================================
    // ÍTEMS
    // =====================================================

    @PostMapping("/items/search")
    public ApiResponse<PageResponse<InventoryItemResponse>> searchItems(@RequestBody InventoryItemSearchRequest request) {
        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.itemsObtenidosCorrectamente",
                service.searchItems(request)
        );
    }

    @GetMapping("/items/{id}")
    public ApiResponse<InventoryItemResponse> getItemById(@PathVariable UUID id) {
        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.itemObtenidoCorrectamente",
                service.getItemById(id)
        );
    }

    @PostMapping("/items/create")
    public ApiResponse<UUID> createItem(@Valid @RequestBody InventoryItemFormRequest request) {
        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.itemRegistradoCorrectamente",
                service.createItem(request)
        );
    }

    @PutMapping("/items/update/{id}")
    public ApiResponse<String> updateItem(@PathVariable UUID id, @Valid @RequestBody InventoryItemFormRequest request) {
        service.updateItem(id, request);
        return new ApiResponse<>(HttpStatus.OK.value(), "success.itemActualizadoCorrectamente", "OK");
    }

    // =====================================================
    // MOVIMIENTOS
    // =====================================================

    @PostMapping("/movements/search")
    public ApiResponse<PageResponse<InventoryMovementResponse>> searchMovements(@RequestBody InventoryMovementSearchRequest request) {
        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.movimientosObtenidosCorrectamente",
                service.searchMovements(request)
        );
    }

    @GetMapping("/movements/{id}")
    public ApiResponse<InventoryMovementResponse> getMovementById(@PathVariable UUID id) {
        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.movimientoObtenidoCorrectamente",
                service.getMovementById(id)
        );
    }

    @PostMapping("/movements/create")
    public ApiResponse<UUID> createMovement(@Valid @RequestBody InventoryMovementFormRequest request) {
        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.movimientoRegistradoCorrectamente",
                service.createMovement(request)
        );
    }

    // =====================================================
    // ASIGNACIONES / CUSTODIA
    // =====================================================

    @PostMapping("/assignments/search")
    public ApiResponse<PageResponse<InventoryAssignmentResponse>> searchAssignments(@RequestBody InventoryAssignmentSearchRequest request) {
        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.asignacionesObtenidasCorrectamente",
                service.searchAssignments(request)
        );
    }

    @GetMapping("/assignments/{id}")
    public ApiResponse<InventoryAssignmentResponse> getAssignmentById(@PathVariable UUID id) {
        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.asignacionObtenidaCorrectamente",
                service.getAssignmentById(id)
        );
    }

    @PostMapping("/assignments/create")
    public ApiResponse<UUID> createAssignment(@Valid @RequestBody InventoryAssignmentFormRequest request) {
        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "success.asignacionRegistradaCorrectamente",
                service.createAssignment(request)
        );
    }

    @PostMapping("/assignments/{id}/return")
    public ApiResponse<String> returnAssignment(@PathVariable UUID id, @RequestBody(required = false) InventoryAssignmentReturnRequest request) {
        service.returnAssignment(id, request);
        return new ApiResponse<>(HttpStatus.OK.value(), "success.devolucionRegistradaCorrectamente", "OK");
    }
}
