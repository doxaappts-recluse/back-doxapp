package pe.dcs.app.features.inventory.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.finance.FinancialMovementPaymentMethod;
import pe.dcs.app.util.enums.inventory.InventoryMovementReason;
import pe.dcs.app.util.enums.inventory.InventoryMovementType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class InventoryMovementFormRequest {

    @NotNull(message = "{error.debeSeleccionarItem}")
    private UUID itemId;
    @NotNull(message = "{error.debeIndicarSiEntradaSalida}")
    private InventoryMovementType type;
    @NotNull(message = "{error.motivoMovimientoObligatorio}")
    private InventoryMovementReason reason;
    @NotNull(message = "{error.cantidadDebeSerMayorCero}")
    private Integer quantity;

    /**
     * Solo relevante si type=IN y reason=PURCHASE — dispara la
     * creación del FinancialMovement vinculado (ver
     * InventoryServiceImpl.syncFinancialMovement). paymentMethod
     * también solo aplica en ese caso.
     */
    private BigDecimal unitCost;
    private FinancialMovementPaymentMethod paymentMethod;

    @NotNull(message = "{error.fechaTransaccionObligatoria}")
    private LocalDate movementDate;
    private String notes;
}
