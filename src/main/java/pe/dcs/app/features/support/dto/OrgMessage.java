package pe.dcs.app.features.support.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Mensaje tal como lo ve la organización: no existe el campo "internal" porque las notas internas jamás se le envían (V5). */
public record OrgMessage(UUID id, String kind, String authorType, String authorName, String body, Instant createdAt,
                         List<AttachmentInfo> attachments) {
}
