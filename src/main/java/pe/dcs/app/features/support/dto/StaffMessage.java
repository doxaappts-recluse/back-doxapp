package pe.dcs.app.features.support.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StaffMessage(UUID id, String kind, String authorType, String authorName, String body, boolean internal,
                           Instant createdAt, List<AttachmentInfo> attachments) {
}
