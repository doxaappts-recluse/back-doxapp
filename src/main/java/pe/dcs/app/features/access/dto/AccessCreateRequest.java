package pe.dcs.app.features.access.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.dcs.app.shared.vo.DocumentType;
import pe.dcs.app.util.enums.RoleType;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Alta de un acceso. La persona es una existente ({@code personId}) o se crea con la identidad mínima
 * (documento, nombre, apellido y correo). {@code profileId} copia un perfil; {@code permissions} se aplica encima.
 */
public record AccessCreateRequest(
        UUID personId,
        DocumentType docType,
        @Size(max = 12, message = "error.common.tooLong") String docNumber,
        @Size(max = 80, message = "error.common.tooLong") String firstName,
        @Size(max = 80, message = "error.common.tooLong") String lastName,
        @Size(max = 160, message = "error.common.tooLong") String email,
        @Size(max = 20, message = "error.common.tooLong") String phone,
        @NotNull(message = "error.common.required") RoleType role,
        UUID branchId,
        LocalDate validFrom,
        LocalDate validTo,
        UUID profileId,
        @Valid List<PermissionItemDto> permissions
) {
}
