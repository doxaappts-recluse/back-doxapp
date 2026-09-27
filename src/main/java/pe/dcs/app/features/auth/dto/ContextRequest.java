package pe.dcs.app.features.auth.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Elegir/cambiar de contexto: acceso ({@code contextId}) + sede ({@code branchId}, obligatoria si la organización tiene
 * sedes activas; para un rol de sede debe ser la suya). {@code refreshToken}: el de la sesión actual, para cerrarla al cambiar.
 */
public record ContextRequest(
        @NotNull(message = "error.common.required") UUID contextId,
        UUID branchId,
        String refreshToken
) {
}
