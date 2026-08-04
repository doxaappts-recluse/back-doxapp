package pe.dcs.app.features.inventory.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.StatusType;

import java.util.UUID;

@Getter
@Setter
public class InventoryItemFormRequest {

    private String name;
    private String description;
    private String category;
    private String unit;
    private Integer minStock;

    /** Solo relevante para org admin (elige sede libremente); igual criterio que el resto de features. */
    private UUID branchId;

    private StatusType status;
}
