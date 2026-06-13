package pe.dcs.app.features.event.service.ticket;

import java.util.UUID;

public interface TicketService {

    byte[] generateTicket(UUID registrationId);

}