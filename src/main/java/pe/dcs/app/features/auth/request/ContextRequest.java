package pe.dcs.app.features.auth.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ContextRequest(
        @NotNull(message = "{error.laOrganizacionEsObligatoria}")
        UUID organizationId,

        // sede: opcional (contexto puede ser solo a nivel organización)
        UUID branchId
) {
}