package pe.dcs.app.features.ministry_role.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class MinistryRoleRequest {
    @NotBlank(message = "{error.codigoRolMinisterialObligatorio}")
    private String code;

    @NotBlank(message = "{error.nombreEsRolMinisterialObligatorio}")
    private String nameEs;

    @NotBlank(message = "{error.nombreEnRolMinisterialObligatorio}")
    private String nameEn;

    private String description;

    @NotNull(message = "{error.ministerioNoEncontrado}")
    private UUID ministryId;

    private Boolean requiresActiveMembership;
}