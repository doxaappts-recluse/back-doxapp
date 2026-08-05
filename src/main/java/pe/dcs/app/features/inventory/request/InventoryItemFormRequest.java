package pe.dcs.app.features.inventory.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.StatusType;

import java.util.UUID;

@Getter
@Setter
public class InventoryItemFormRequest {

    @NotBlank(message = "{error.nombreItemObligatorio}")
    private String name;
    private String description;
    @NotBlank(message = "{error.categoriaObligatoria}")
    private String category;
    @NotBlank(message = "{error.unidadMedidaObligatoria}")
    private String unit;
    @NotNull(message = "{error.stockMinimoObligatorio}")
    private Integer minStock;

    /** Solo relevante para org admin (elige sede libremente); igual criterio que el resto de features. */
    private UUID branchId;

    private StatusType status;
}
