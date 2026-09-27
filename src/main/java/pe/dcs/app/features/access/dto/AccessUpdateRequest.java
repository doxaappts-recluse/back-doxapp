package pe.dcs.app.features.access.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** Edición: vigencia, permisos (solo ORG_USER; nulo = sin cambios, lista vacía = quitar todo) y correo mientras siga invitada. */
public record AccessUpdateRequest(
        LocalDate validFrom,
        LocalDate validTo,
        boolean clearValidTo,
        @Valid List<PermissionItemDto> permissions,
        @Size(max = 160, message = "error.common.tooLong") String email,
        Long version
) {
}
