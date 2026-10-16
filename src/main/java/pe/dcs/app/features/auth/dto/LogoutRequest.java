package pe.dcs.app.features.auth.dto;

/** El refresh es opcional: sin él el logout es un no-op (el token de acceso vence solo). */
public record LogoutRequest(String refreshToken) {
}
