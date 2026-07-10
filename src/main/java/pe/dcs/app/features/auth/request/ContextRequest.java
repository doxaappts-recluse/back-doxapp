package pe.dcs.app.features.auth.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ContextRequest(
        @NotNull
        UUID organizationId,

        UUID branchId
) {
}