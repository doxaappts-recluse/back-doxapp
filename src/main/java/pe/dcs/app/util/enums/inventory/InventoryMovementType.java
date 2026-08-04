package pe.dcs.app.util.enums.inventory;

/**
 * Tipo de movimiento de stock de un {@link pe.dcs.app.entity.InventoryItem}.
 * IN aumenta InventoryItem.currentQuantity, OUT lo disminuye (ver
 * InventoryServiceImpl.applyStockChange). No confundir con
 * InventoryAssignment (custodia) — un OUT reduce stock real
 * (consumo/pérdida), una asignación solo registra quién tiene
 * prestado un ítem, sin tocar la cantidad total.
 */
public enum InventoryMovementType {
    IN,
    OUT
}
