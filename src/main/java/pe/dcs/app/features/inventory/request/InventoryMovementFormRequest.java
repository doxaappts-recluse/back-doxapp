package pe.dcs.app.features.inventory.request;

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

    private UUID itemId;
    private InventoryMovementType type;
    private InventoryMovementReason reason;
    private Integer quantity;

    /**
     * Solo relevante si type=IN y reason=PURCHASE — dispara la
     * creación del FinancialMovement vinculado (ver
     * InventoryServiceImpl.syncFinancialMovement). paymentMethod
     * también solo aplica en ese caso.
     */
    private BigDecimal unitCost;
    private FinancialMovementPaymentMethod paymentMethod;

    private LocalDate movementDate;
    private String notes;
}
