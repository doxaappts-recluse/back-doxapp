package pe.dcs.app.features.user.access_user.response;

import lombok.*;
import pe.dcs.app.util.auditable.AuditableResponse;

import java.util.UUID;

/**
 * Fila de listado.
 *
 * "id" es el id del ACCESO (UserAccess), no de la persona: una
 * persona puede tener varios accesos ORG_USER (uno por sede), y
 * cada uno se lista/gestiona de forma independiente.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessUserResponse extends AuditableResponse {

    private UUID id;

    private UUID personId;

    private String name;
    private String lastname;

    private String username;

    private Boolean hasCredential;
    private Boolean credentialActive;

    private Boolean accessActive;

    private UUID organizationId;
    private String organizationName;

    private UUID branchId;
    private String branchName;

}
