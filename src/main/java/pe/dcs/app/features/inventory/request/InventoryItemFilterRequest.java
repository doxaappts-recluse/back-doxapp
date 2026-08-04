package pe.dcs.app.features.inventory.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.StatusType;

import java.util.UUID;

@Getter
@Setter
public class InventoryItemFilterRequest {

    private String name;
    private String category;
    private UUID branchId;
    private StatusType status;

    /** Si true, solo ítems con minStock informado y currentQuantity <= minStock. */
    private Boolean lowStockOnly;
}
