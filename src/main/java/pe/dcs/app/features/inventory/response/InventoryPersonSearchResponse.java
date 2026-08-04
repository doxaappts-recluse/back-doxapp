package pe.dcs.app.features.inventory.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** Buscar persona por DNI para asignarla como custodio de un ítem — mismo patrón que SpaceReservation/BibleAcademy. */
@Getter
@Setter
@AllArgsConstructor
public class InventoryPersonSearchResponse {

    private UUID personId;
    private String name;
    private String lastname;
    private String dni;
    private boolean isMember;
}
