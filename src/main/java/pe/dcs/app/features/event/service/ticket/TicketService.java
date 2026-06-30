package pe.dcs.app.features.event.service.ticket;

import pe.dcs.app.features.event.response.event.TicketResponse;
import pe.dcs.app.features.event.response.event.TicketTemplateResponse;

import java.util.UUID;

public interface TicketService {

    byte[] generateTicket(UUID registrationId);

    TicketResponse getTicket(UUID registrationId);

    TicketTemplateResponse getTicketTemplate(UUID eventId);


}