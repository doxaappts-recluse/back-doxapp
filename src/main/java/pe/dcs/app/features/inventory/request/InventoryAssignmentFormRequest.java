package pe.dcs.app.features.inventory.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/** Exactamente uno de assignedToPersonId/assignedToMinistryId debe venir informado — ver InventoryServiceImpl.validateAssignmentForm. */
@Getter
@Setter
public class InventoryAssignmentFormRequest {

    private UUID itemId;
    private Integer quantity;

    private UUID assignedToPersonId;
    private UUID assignedToMinistryId;

    private LocalDate assignedDate;
    private LocalDate expectedReturnDate;
    private String notes;
}
