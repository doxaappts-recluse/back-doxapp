package pe.dcs.app.features.support.dto;

import java.util.UUID;

public record AttachmentInfo(UUID id, String fileName, String contentType, long sizeBytes) {
}
