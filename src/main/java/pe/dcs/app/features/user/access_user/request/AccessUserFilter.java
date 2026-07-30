package pe.dcs.app.features.user.access_user.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.StatusType;

import java.util.UUID;

@Getter
@Setter
public class AccessUserFilter {

    /**
     * Opcional. Solo lo usa ORG_ADMIN para acotar a una
     * sede en particular; ORG_BRANCH_ADMIN siempre queda
     * fijo a la suya sin importar lo que llegue acá.
     */
    private UUID branchId;

    private String name;

    private String lastname;

    private String dni;

    private String username;

    private Boolean hasCredential;
    private Boolean credentialActive;

    private StatusType status;

}
