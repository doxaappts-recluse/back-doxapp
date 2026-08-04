package pe.dcs.app.features.pastoral_followup.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.pagination.PaginationRequest;
import pe.dcs.app.util.pagination.SortRequest;

import java.util.List;

/**
 * Igual forma para el historial de contactos y para el listado de
 * peticiones de oración de una persona (ver
 * PastoralFollowUpController) — mismo patrón que
 * MembershipHistoryRequest.
 */
@Getter
@Setter
public class PastoralFollowUpHistoryRequest {

    private PaginationRequest pagination;

    private List<SortRequest> sorts;
}
