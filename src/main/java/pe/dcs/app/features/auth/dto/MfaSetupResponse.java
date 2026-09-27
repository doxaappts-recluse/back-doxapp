package pe.dcs.app.features.auth.dto;

/** Secreto TOTP recién generado y su URI otpauth:// (el front la dibuja como QR). El secreto se muestra una sola vez. */
public record MfaSetupResponse(String secret, String otpAuthUri) {
}
