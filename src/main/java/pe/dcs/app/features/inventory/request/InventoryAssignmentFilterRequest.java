package pe.dcs.app.features.inventory.request;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class InventoryAssignmentFilterRequest {

    private UUID itemId;
    private UUID assignedToPersonId;
    private UUID assignedToMinistryId;

    /** Si true, solo asignaciones sin devolver (returnedDate null). */
    private Boolean activeOnly;
}
