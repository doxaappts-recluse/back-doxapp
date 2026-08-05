package pe.dcs.app.features.inventory.mapper;

import org.springframework.stereotype.Component;
import pe.dcs.app.entity.InventoryAssignment;
import pe.dcs.app.entity.InventoryItem;
import pe.dcs.app.entity.InventoryMovement;
import pe.dcs.app.features.inventory.response.InventoryAssignmentResponse;
import pe.dcs.app.features.inventory.response.InventoryItemResponse;
import pe.dcs.app.features.inventory.response.InventoryMovementResponse;
import pe.dcs.app.util.auditable.BaseMapper;

@Component
public class InventoryMapper {

    public InventoryItemResponse toItemResponse(
            InventoryItem item,
            long movementCount,
            long activeAssignmentCount,
            boolean canManage,
            boolean showAudit
    ) {

        InventoryItemResponse response = new InventoryItemResponse();

        BaseMapper.mapAudit(item, response, showAudit);

        response.setId(item.getId());
        response.setName(item.getName());
        response.setDescription(item.getDescription());
        response.setCategory(item.getCategory());
        response.setUnit(item.getUnit());
        response.setCurrentQuantity(item.getCurrentQuantity());
        response.setMinStock(item.getMinStock());
        response.setLowStock(
                item.getMinStock() != null
                        && item.getCurrentQuantity() != null
                        && item.getCurrentQuantity() <= item.getMinStock()
        );
        response.setStatus(item.getStatus());
        response.setMovementCount(movementCount);
        response.setActiveAssignmentCount(activeAssignmentCount);
        response.setCanManage(canManage);

        if (item.getBranch() != null) {
            response.setBranchId(item.getBranch().getId());
            response.setBranchName(item.getBranch().getName());
        }

        return response;
    }

    public InventoryMovementResponse toMovementResponse(
            InventoryMovement movement,
            boolean canManage,
            boolean showAudit
    ) {

        InventoryMovementResponse response = new InventoryMovementResponse();

        BaseMapper.mapAudit(movement, response, showAudit);

        response.setId(movement.getId());
        response.setType(movement.getType());
        response.setReason(movement.getReason());
        response.setQuantity(movement.getQuantity());
        response.setUnitCost(movement.getUnitCost());
        response.setTotalCost(movement.getTotalCost());
        response.setMovementDate(movement.getMovementDate());
        response.setNotes(movement.getNotes());
        response.setCanManage(canManage);

        if (movement.getFinancialMovement() != null) {
            response.setFinancialMovementId(movement.getFinancialMovement().getId());
        }

        InventoryItem item = movement.getItem();

        if (item != null) {
            response.setItemId(item.getId());
            response.setItemName(item.getName());

            if (item.getBranch() != null) {
                response.setBranchId(item.getBranch().getId());
                response.setBranchName(item.getBranch().getName());
            }
        }

        return response;
    }

    public InventoryAssignmentResponse toAssignmentResponse(
            InventoryAssignment assignment,
            boolean canManage,
            boolean showAudit
    ) {

        InventoryAssignmentResponse response = new InventoryAssignmentResponse();

        BaseMapper.mapAudit(assignment, response, showAudit);

        response.setId(assignment.getId());
        response.setQuantity(assignment.getQuantity());
        response.setAssignedDate(assignment.getAssignedDate());
        response.setExpectedReturnDate(assignment.getExpectedReturnDate());
        response.setReturnedDate(assignment.getReturnedDate());
        response.setActive(assignment.getReturnedDate() == null);
        response.setNotes(assignment.getNotes());
        response.setCanManage(canManage);

        if (assignment.getAssignedToPerson() != null) {
            response.setAssignedToPersonId(assignment.getAssignedToPerson().getId());
            response.setAssignedToPersonName(
                    assignment.getAssignedToPerson().getName() + " " + assignment.getAssignedToPerson().getLastname()
            );
        }

        if (assignment.getAssignedToMinistry() != null) {
            response.setAssignedToMinistryId(assignment.getAssignedToMinistry().getId());
            response.setAssignedToMinistryName(assignment.getAssignedToMinistry().getLocalizedName());
        }

        InventoryItem item = assignment.getItem();

        if (item != null) {
            response.setItemId(item.getId());
            response.setItemName(item.getName());

            if (item.getBranch() != null) {
                response.setBranchId(item.getBranch().getId());
                response.setBranchName(item.getBranch().getName());
            }
        }

        return response;
    }
}
