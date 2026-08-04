package pe.dcs.app.features.pastoral_followup.response;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Encabezado del bloque "Seguimiento Pastoral" dentro del detalle de
 * una Person (ver PastoralFollowUpServiceImpl.getSummary): quién es
 * el líder asignado + si el usuario actual puede gestionar este
 * seguimiento (mostrar/ocultar acciones en el front). El historial
 * de contactos y las peticiones de oración se piden aparte (paginado)
 * vía /contacts y /prayer-requests.
 */
@Getter
@Setter
public class PastoralFollowUpSummaryResponse {

    private UUID personId;

    private UUID assignedLeaderId;
    private String assignedLeaderName;

    private boolean canManage;
}
