package pe.dcs.app.features.event.request.registration;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.events.RegistrationCategory;
import pe.dcs.app.util.pagination.PaginationRequest;

import java.util.UUID;

/**
 * Búsqueda de personas ya registradas en la organización para
 * inscribirlas a un evento como Miembro o Staff (categorías
 * "internas" — ver EventRegistrationServiceImpl.isInternalCategory).
 * El scoping por sede/organización lo decide el service según
 * quién busca y el scope del evento, no este request.
 */
@Getter
@Setter
public class EventPersonSearchRequest {

    private UUID eventId;

    /**
     * Solo MEMBER o STAFF. MEMBER exige membresía activa vigente;
     * STAFF busca cualquier persona registrada en la sede, tenga
     * o no membresía.
     */
    private RegistrationCategory category;

    private String name;

    private PaginationRequest pagination;
}
