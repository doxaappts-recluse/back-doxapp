package pe.dcs.app.features.familygroup.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.FamilyRole;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class FamilyMemberFormRequest {

    /**
     * Obligatorio — a diferencia de SmallGroupMember, Grupo Familiar
     * no admite invitados de solo nombre: siempre opera sobre una
     * Person que ya existe (encontrada por DNI, ver
     * FamilyGroupController.findPersonByDni).
     */
    private UUID personId;

    private FamilyRole role;

    private LocalDate joinDate;
}
