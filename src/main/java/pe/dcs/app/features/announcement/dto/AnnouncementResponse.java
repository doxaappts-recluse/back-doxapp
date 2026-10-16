package pe.dcs.app.features.announcement.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AnnouncementResponse(UUID id, String title, String body, String severity, String audienceType, List<UUID> audienceIds,
                                   List<String> audienceLabels, Instant startsAt, Instant endsAt, boolean dismissible,
                                   boolean portalVisible, String status, String phase, Instant publishedAt, Instant createdAt, Long version) {
}
