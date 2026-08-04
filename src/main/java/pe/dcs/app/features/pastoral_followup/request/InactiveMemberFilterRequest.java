package pe.dcs.app.features.pastoral_followup.request;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class InactiveMemberFilterRequest {

    /**
     * Busca por nombre o apellido de la persona (contains,
     * case-insensitive) — ver InactiveMemberSpecification.
     */
    private String personName;

    /**
     * true = solo personas con líder asignado; false = solo sin
     * asignar; null = sin filtrar. Útil para priorizar a quién le
     * falta un responsable de seguimiento.
     */
    private Boolean hasAssignedLeader;

    /**
     * Solo relevante para org admin/SYSTEM; para branch admin/org
     * user delegado el scope ya lo fija InactiveMemberSpecification
     * con la sede actual.
     */
    private UUID branchId;
}
