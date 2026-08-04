package pe.dcs.app.features.inventory.response;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.auditable.AuditableResponse;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class InventoryAssignmentResponse extends AuditableResponse {

    private UUID id;

    private UUID itemId;
    private String itemName;

    private UUID branchId;
    private String branchName;

    private Integer quantity;

    private UUID assignedToPersonId;
    private String assignedToPersonName;

    private UUID assignedToMinistryId;
    private String assignedToMinistryName;

    private LocalDate assignedDate;
    private LocalDate expectedReturnDate;
    private LocalDate returnedDate;

    /** true mientras returnedDate sea null. */
    private boolean active;

    private String notes;

    private boolean canManage;
}
