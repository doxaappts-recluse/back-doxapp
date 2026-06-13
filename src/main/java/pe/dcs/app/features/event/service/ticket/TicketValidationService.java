package pe.dcs.app.features.event.service.ticket;

import pe.dcs.app.features.event.response.attendance.TicketValidationResponse;

public interface TicketValidationService {

    TicketValidationResponse validate(String qrToken);

}