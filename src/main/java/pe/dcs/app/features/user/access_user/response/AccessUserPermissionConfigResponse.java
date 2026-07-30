package pe.dcs.app.features.user.access_user.response;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class AccessUserPermissionConfigResponse {

    private UUID id;
    private String code;
    private String name;

    /**
     * Si el usuario ya tiene este permiso
     * asignado dentro del módulo.
     */
    private boolean assigned;

    public AccessUserPermissionConfigResponse(
            UUID id,
            String code,
            String name,
            boolean assigned
    ) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.assigned = assigned;
    }

}
