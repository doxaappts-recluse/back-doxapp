package pe.dcs.app.features.familygroup.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.StatusType;

import java.util.UUID;

@Getter
@Setter
public class FamilyGroupFormRequest {

    /**
     * Opcional — si viene en blanco, el service arma un nombre por
     * defecto ("Familia <apellido del jefe de hogar>").
     */
    private String name;

    private String observations;

    /**
     * Solo obligatorio en create(): persona encontrada por DNI (ver
     * FamilyGroupController.findPersonByDni) que se agrega de una vez
     * como HEAD_OF_HOUSEHOLD. En update() se ignora — los miembros se
     * gestionan con los endpoints de member. No se valida acá con
     * @NotNull porque en update() el frontend lo envía en null (ver
     * FamilyGroupServiceImpl.create validando el caso obligatorio).
     */
    private UUID headPersonId;

    private StatusType status;

    /**
     * Solo relevante para org admin (elige sede libremente); igual
     * criterio que el resto de features (Marriage, SmallGroup).
     */
    private UUID branchId;
}
