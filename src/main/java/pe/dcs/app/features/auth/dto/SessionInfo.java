package pe.dcs.app.features.auth.dto;

import java.time.Instant;
import java.util.UUID;

public record SessionInfo(UUID id, Instant lastActivityAt, String ip, String userAgent) {
}
