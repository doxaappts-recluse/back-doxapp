package pe.dcs.app.features.inventory.response;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.auditable.AuditableResponse;
import pe.dcs.app.util.enums.inventory.InventoryMovementReason;
import pe.dcs.app.util.enums.inventory.InventoryMovementType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class InventoryMovementResponse extends AuditableResponse {

    private UUID id;

    private UUID itemId;
    private String itemName;

    private UUID branchId;
    private String branchName;

    private InventoryMovementType type;
    private InventoryMovementReason reason;
    private Integer quantity;

    private BigDecimal unitCost;
    private BigDecimal totalCost;

    private LocalDate movementDate;
    private String notes;

    /** Informado solo si esta compra generó un FinancialMovement (ver InventoryServiceImpl.syncFinancialMovement). */
    private UUID financialMovementId;

    private boolean canManage;
}
