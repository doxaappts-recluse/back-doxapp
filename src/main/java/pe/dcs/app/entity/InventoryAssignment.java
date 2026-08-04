package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.auditable.Auditable;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Custodia de un {@link InventoryItem} asignado a una Person o a un
 * Ministry (exactamente uno de los dos, ver
 * InventoryServiceImpl.validateAssignmentForm) — un activo prestado
 * (proyector, instrumento) mientras returnedDate sea null. A
 * diferencia de InventoryMovement, esto NO descuenta
 * InventoryItem.currentQuantity: es un seguimiento paralelo de "quién
 * lo tiene", no un consumo de stock — el ítem sigue siendo propiedad
 * de la sede, solo está prestado.
 */
@Entity
@Table(
        name = "inventory_assignments",
        indexes = {
                @Index(name = "idx_inventory_assignment_item", columnList = "item_id"),
                @Index(name = "idx_inventory_assignment_person", columnList = "assigned_to_person_id"),
                @Index(name = "idx_inventory_assignment_ministry", columnList = "assigned_to_ministry_id")
        }
)
@Getter
@Setter
public class InventoryAssignment extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private InventoryItem item;

    @Column(nullable = false)
    private Integer quantity = 1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_person_id")
    private Person assignedToPerson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_ministry_id")
    private Ministry assignedToMinistry;

    @Column(name = "assigned_date", nullable = false)
    private LocalDate assignedDate;

    @Column(name = "expected_return_date")
    private LocalDate expectedReturnDate;

    /** Null = sigue en custodia (activo). Informada = ya se devolvió. */
    @Column(name = "returned_date")
    private LocalDate returnedDate;

    @Column(length = 1000)
    private String notes;
}
