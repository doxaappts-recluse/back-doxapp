package pe.dcs.app.features.inventory.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** returnedDate nulo => se usa la fecha actual (ver InventoryServiceImpl.returnAssignment). */
@Getter
@Setter
public class InventoryAssignmentReturnRequest {

    private LocalDate returnedDate;
    private String notes;
}
