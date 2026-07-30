package pe.dcs.app.features.user.access_user.response;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class AccessUserModuleConfigResponse {

    private UUID moduleId;
    private String name;

    /**
     * Si el usuario ya tiene asignado
     * (al menos parcialmente) este módulo.
     */
    private boolean assigned;

    private List<AccessUserPermissionConfigResponse> permissions =
            new ArrayList<>();

}
