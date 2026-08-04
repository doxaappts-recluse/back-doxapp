package pe.dcs.app.util.enums.finance;

/**
 * Categoría del movimiento. TITHE/OFFERING/DONATION/OTHER_INCOME
 * son de tipo INCOME; EXPENSE/INVENTORY_PURCHASE/PAYROLL son de
 * tipo EXPENSE — la relación categoría→tipo se valida en el service
 * (ver FinancialMovementServiceImpl.deriveType).
 */
public enum FinancialMovementCategory {
    TITHE,               // Diezmo
    OFFERING,            // Ofrenda
    DONATION,            // Donación
    OTHER_INCOME,        // Ingreso general
    SERVICE_FEE,         // Cobro por servicio (matrimonio, certificados, etc.)
    EXPENSE,             // Gasto
    INVENTORY_PURCHASE,  // Compra de inventario (ver InventoryServiceImpl.syncFinancialMovement)
    PAYROLL              // Pago de planilla (ver HrServiceImpl.syncFinancialMovement)
}
