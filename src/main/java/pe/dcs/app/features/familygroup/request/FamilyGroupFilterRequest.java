package pe.dcs.app.features.familygroup.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.StatusType;

import java.util.UUID;

@Getter
@Setter
public class FamilyGroupFilterRequest {

    private String name;

    private StatusType status;

    /**
     * Busca el grupo familiar al que pertenece la persona con este
     * DNI (join contra FamilyMember.person.dni) — útil para ubicar
     * rápido la familia de alguien desde el listado.
     */
    private String memberDni;

    /**
     * Solo relevante para org admin/SYSTEM; para branch admin/org
     * user delegado el scope ya lo fija FamilyGroupSpecification con
     * la sede/permiso actual.
     */
    private UUID branchId;
}
