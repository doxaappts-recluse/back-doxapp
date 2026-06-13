package pe.dcs.app.features.event.request.attendance;

public record TicketValidationRequest(
        String qrToken
) {}