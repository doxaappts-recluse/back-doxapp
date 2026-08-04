package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.auditable.Auditable;
import pe.dcs.app.util.enums.StatusType;

import java.util.UUID;

/**
 * Ítem de inventario (catálogo por sede, no delegable — ver
 * InventoryAccessGuard.assertCanCreateItem, mismo criterio que
 * ReservableSpace). currentQuantity es un valor denormalizado que
 * InventoryServiceImpl mantiene al día con cada InventoryMovement
 * (IN suma, OUT resta) — no se edita directamente desde el form del
 * ítem, solo se inicializa en 0 al crear.
 *
 * minStock es opcional y solo habilita el indicador "bajo stock" en
 * InventoryItemResponse.lowStock (currentQuantity <= minStock) — no
 * dispara ninguna notificación automática en v1.
 */
@Entity
@Table(
        name = "inventory_items",
        indexes = {
                @Index(name = "idx_inventory_item_branch", columnList = "branch_id"),
                @Index(name = "idx_inventory_item_status", columnList = "status")
        }
)
@Getter
@Setter
public class InventoryItem extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    /** Texto libre (ej: "Mobiliario", "Sonido", "Limpieza") — sin catálogo aparte, igual que SmallGroup.category. */
    private String category;

    /** Unidad de medida (ej: "unidad", "caja", "paquete"). Por defecto "unidad". */
    @Column(nullable = false)
    private String unit = "unidad";

    @Column(name = "current_quantity", nullable = false)
    private Integer currentQuantity = 0;

    @Column(name = "min_stock")
    private Integer minStock;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusType status;
}
