package pe.dcs.app.features.support.dto;

public record AttachmentContent(String fileName, String contentType, byte[] data) {
}
