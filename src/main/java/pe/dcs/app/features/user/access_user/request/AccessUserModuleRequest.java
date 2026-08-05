package pe.dcs.app.features.user.access_user.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Módulo (hijo) asignado al usuario, junto con
 * los permisos que se le habilitan dentro de ese módulo.
 */
@Getter
@Setter
public class AccessUserModuleRequest {

    @NotNull(message = "{error.accessUserModuloObligatorio}")
    private UUID moduleId;

    private List<UUID> permissionIds = new ArrayList<>();

}
