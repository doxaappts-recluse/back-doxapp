package pe.dcs.app.features.inventory.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/** Exactamente uno de assignedToPersonId/assignedToMinistryId debe venir informado — ver InventoryServiceImpl.validateAssignmentForm. */
@Getter
@Setter
public class InventoryAssignmentFormRequest {

    @NotNull(message = "{error.debeSeleccionarItem}")
    private UUID itemId;
    @NotNull(message = "{error.cantidadDebeSerMayorCero}")
    private Integer quantity;

    private UUID assignedToPersonId;
    private UUID assignedToMinistryId;

    @NotNull(message = "{error.fechaAsignacionObligatoria}")
    private LocalDate assignedDate;
    private LocalDate expectedReturnDate;
    private String notes;
}
