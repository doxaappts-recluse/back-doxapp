package pe.dcs.app.features.smallgroup.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Resultado de buscar una persona por DNI para asignarla como líder
 * o como miembro de un grupo pequeño (ver
 * SmallGroupServiceImpl.findPersonByDni). isMember es solo
 * informativo — a diferencia de Marriage, aquí NO dispara ninguna
 * actualización automática de estado, ya que los grupos pequeños no
 * son exclusivos de miembros.
 */
@Getter
@Setter
@AllArgsConstructor
public class SmallGroupPersonSearchResponse {

    private UUID personId;

    private String name;

    private String lastname;

    private String dni;

    private boolean isMember;
}
