package pe.dcs.app.features.familygroup.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Resultado de buscar una Person por DNI para agregarla a un grupo
 * familiar (como jefe de hogar o como miembro). Si la persona ya
 * pertenece a OTRO grupo familiar, existingFamilyGroupId/Name vienen
 * informados — el frontend puede advertir antes de agregarla (una
 * Person pertenece a un solo grupo familiar a la vez).
 */
@Getter
@Setter
@AllArgsConstructor
public class FamilyGroupPersonSearchResponse {

    private UUID personId;
    private String name;
    private String lastname;
    private String dni;
    private boolean member;

    private UUID existingFamilyGroupId;
    private String existingFamilyGroupName;
}
