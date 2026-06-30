package pe.dcs.app.features.event.service.ticket;

import pe.dcs.app.features.event.response.event.TicketTemplateResponse;

import java.util.UUID;

public interface TicketService {

    byte[] generateTicket(UUID registrationId);

    TicketTemplateResponse getTicketTemplate(UUID eventId);


}