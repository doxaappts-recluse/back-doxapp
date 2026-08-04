package pe.dcs.app.features.inventory.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.inventory.InventoryMovementReason;
import pe.dcs.app.util.enums.inventory.InventoryMovementType;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class InventoryMovementFilterRequest {

    private UUID itemId;
    private InventoryMovementType type;
    private InventoryMovementReason reason;
    private LocalDate fromDate;
    private LocalDate toDate;

    /**
     * Solo relevante para org admin (acotar a una sede puntual
     * dentro de su organización, ver Reportes Avanzados); branch
     * admin/org user delegado ya queda fijado a su sede actual por
     * InventoryMovementSpecification.
     */
    private UUID branchId;
}
