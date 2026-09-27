package pe.dcs.app.features.announcement.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** audienceType: ALL | ORGS | PLANS. audienceIds: ids de organizaciones o de planes (obligatorio si no es ALL). */
public record AnnouncementRequest(String title, String body, String severity, String audienceType, List<UUID> audienceIds,
                                  Instant startsAt, Instant endsAt, Boolean dismissible, Boolean portalVisible, Long version) {
}
