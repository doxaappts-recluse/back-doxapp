package pe.dcs.app.features.notification.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record NotificationView(UUID id, String type, String category, Map<String, String> params, String link, Instant createdAt, Instant readAt) {
}
