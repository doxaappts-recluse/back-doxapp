package pe.dcs.app.features.pastoral_followup.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Fila de "Miembros Inactivos": une Membership (status=INACTIVE,
 * current=true) con lo que ya existe en Seguimiento Pastoral (líder
 * asignado, último contacto) — no persiste nada nuevo, solo cruza
 * datos de dos features ya construidos (Membership + PastoralFollowUp)
 * para dar visibilidad de a quién hay que recontactar.
 */
@Getter
@Setter
public class InactiveMemberResponse {

    private UUID personId;
    private String personName;
    private String personLastname;
    private String personDni;
    private String personPhone;

    private LocalDate membershipStartDate;

    private UUID assignedLeaderId;
    private String assignedLeaderName;

    /**
     * Fecha del contacto más reciente registrado (cualquier
     * resultado) — null si nunca se registró un contacto. Ver
     * PastoralFollowUpServiceImpl.searchInactiveMembers.
     */
    private LocalDate lastContactDate;
    private String lastContactResult;

    private UUID branchId;
    private String branchName;
}
