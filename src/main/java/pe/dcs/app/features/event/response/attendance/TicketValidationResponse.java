package pe.dcs.app.features.event.response.attendance;
import java.time.LocalDateTime;
import java.util.UUID;

public record TicketValidationResponse(

        boolean valid,
        String message,

        UUID registrationId,
        UUID eventId,

        String eventName,
        String attendeeName,

        LocalDateTime usedAt,

        boolean alreadyUsed,

        boolean attendanceCreated
) {}