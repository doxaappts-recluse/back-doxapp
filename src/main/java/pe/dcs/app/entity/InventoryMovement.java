package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.auditable.Auditable;
import pe.dcs.app.util.enums.inventory.InventoryMovementReason;
import pe.dcs.app.util.enums.inventory.InventoryMovementType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Movimiento de stock (entrada/salida) de un {@link InventoryItem}.
 * Delegable a org user con permiso CREATE/EDIT (ver
 * InventoryAccessGuard) a diferencia del catálogo de ítems.
 *
 * Si type=IN, reason=PURCHASE y unitCost viene informado (>0), se
 * crea/sincroniza automáticamente un {@link FinancialMovement} de
 * categoría INVENTORY_PURCHASE (ver
 * InventoryServiceImpl.syncFinancialMovement) — a diferencia del
 * vínculo sourceType/sourceId sin FK real de SpaceReservation, acá
 * SÍ hay una FK real a FinancialMovement porque Finanzas
 * Institucionales es un módulo transversal ya acoplado por varias
 * features (Matrimonios, Bautizo, Caja Diaria), no un módulo par
 * como Eventos/Grupos Pequeños.
 */
@Entity
@Table(
        name = "inventory_movements",
        indexes = {
                @Index(name = "idx_inventory_movement_item", columnList = "item_id"),
                @Index(name = "idx_inventory_movement_type", columnList = "type"),
                @Index(name = "idx_inventory_movement_date", columnList = "movement_date")
        }
)
@Getter
@Setter
public class InventoryMovement extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private InventoryItem item;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InventoryMovementType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InventoryMovementReason reason;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_cost", precision = 12, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "total_cost", precision = 12, scale = 2)
    private BigDecimal totalCost;

    @Column(name = "movement_date", nullable = false)
    private LocalDate movementDate;

    @Column(length = 1000)
    private String notes;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "financial_movement_id")
    private FinancialMovement financialMovement;
}
