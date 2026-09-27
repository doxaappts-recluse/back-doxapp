package pe.dcs.app.features.announcement.dto;

import java.time.Instant;
import java.util.UUID;

/** Lo que ve la organización o el portal: sin audiencia ni estado interno. */
public record ActiveAnnouncement(UUID id, String title, String body, String severity, boolean dismissible, Instant startsAt, Instant endsAt) {
}
