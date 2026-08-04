package pe.dcs.app.features.inventory.service;

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
import pe.dcs.app.util.pagination.PageResponse;

import java.util.List;
import java.util.UUID;

public interface InventoryService {

    /** Buscar persona por DNI para asignarla como custodio — mismo patrón que SpaceReservation/Academia Bíblica. */
    InventoryPersonSearchResponse findPersonByDni(String dni);

    /** Catálogo simple de ministerios ACTIVOS (org-wide) para el select "Asignar a ministerio". */
    List<InventoryMinistryOptionResponse> listMinistries();

    // Ítems
    PageResponse<InventoryItemResponse> searchItems(InventoryItemSearchRequest request);

    InventoryItemResponse getItemById(UUID id);

    UUID createItem(InventoryItemFormRequest request);

    void updateItem(UUID id, InventoryItemFormRequest request);

    // Movimientos
    PageResponse<InventoryMovementResponse> searchMovements(InventoryMovementSearchRequest request);

    InventoryMovementResponse getMovementById(UUID id);

    UUID createMovement(InventoryMovementFormRequest request);

    // Asignaciones / Custodia
    PageResponse<InventoryAssignmentResponse> searchAssignments(InventoryAssignmentSearchRequest request);

    InventoryAssignmentResponse getAssignmentById(UUID id);

    UUID createAssignment(InventoryAssignmentFormRequest request);

    void returnAssignment(UUID id, InventoryAssignmentReturnRequest request);
}
