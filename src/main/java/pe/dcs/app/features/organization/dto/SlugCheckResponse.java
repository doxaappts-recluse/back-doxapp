package pe.dcs.app.features.organization.dto;

/** ¿Se puede usar este identificador? Si no, {@code reason} es la clave de error y {@code message} el texto ya traducido. */
public record SlugCheckResponse(String slug, boolean available, String reason, String message) {
}
