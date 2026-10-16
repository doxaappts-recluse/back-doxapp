package pe.dcs.app.features.auth.dto;

import java.time.Instant;
import java.util.List;

/**
 * Respuesta única de los pasos de autenticación. {@code token} es el de acceso cuando el estado es AUTHENTICATED;
 * en los demás estados es un token intermedio de corta vida que solo sirve para el siguiente paso.
 * {@code refreshToken} solo viaja con AUTHENTICATED.
 */
public record AuthResponse(
        AuthStatus status,
        String token,
        Instant expiresAt,
        String refreshToken,
        List<SetupStep> setup,
        List<ContextInfo> contexts,
        UserInfo user,
        ContextInfo context,
        String contractState,
        Integer idleMinutes,
        List<OrganizationChoice> organizations
) {
}
