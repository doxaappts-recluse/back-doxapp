package pe.dcs.app.features.inventory.response;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.auditable.AuditableResponse;
import pe.dcs.app.util.enums.StatusType;

import java.util.UUID;

@Getter
@Setter
public class InventoryItemResponse extends AuditableResponse {

    private UUID id;

    private String name;
    private String description;
    private String category;
    private String unit;

    private Integer currentQuantity;
    private Integer minStock;

    /** true si minStock está informado y currentQuantity <= minStock. */
    private boolean lowStock;

    private UUID branchId;
    private String branchName;

    private StatusType status;

    private long movementCount;
    private long activeAssignmentCount;

    private boolean canManage;
}
