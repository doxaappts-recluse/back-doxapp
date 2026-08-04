package pe.dcs.app.features.hr.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** Buscar persona por DNI para crear una ficha de empleado — mismo patrón que Inventory/SpaceReservation/BibleAcademy. */
@Getter
@Setter
@AllArgsConstructor
public class HrPersonSearchResponse {

    private UUID personId;
    private String name;
    private String lastname;
    private String dni;
    private boolean isMember;
}
