package pe.dcs.app.util.enums.inventory;

/**
 * Motivo de un movimiento de stock. Solo PURCHASE (con costo
 * informado) dispara la creación automática del FinancialMovement
 * vinculado (categoría INVENTORY_PURCHASE) — ver
 * InventoryServiceImpl.syncFinancialMovement. El resto son
 * puramente informativos.
 */
public enum InventoryMovementReason {
    PURCHASE,       // Compra (IN) — puede generar FinancialMovement
    DONATION,       // Donación recibida (IN)
    CONSUMPTION,    // Consumo (OUT)
    LOSS,           // Pérdida o merma (OUT)
    ADJUSTMENT,     // Ajuste de inventario, cualquier sentido
    OTHER
}
