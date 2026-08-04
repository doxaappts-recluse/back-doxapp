package pe.dcs.app.features.report.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class InventoryCard {

    /** Ítems ACTIVE con stock actual <= minStock. */
    private long lowStockItems;

    /** Asignaciones/custodias aún no devueltas. */
    private long activeAssignments;
}
