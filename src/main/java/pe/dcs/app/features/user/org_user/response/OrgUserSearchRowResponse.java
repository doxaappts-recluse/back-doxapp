package pe.dcs.app.features.user.org_user.response;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.auditable.AuditableResponse;

import java.util.UUID;

@Getter
@Setter
public class OrgUserSearchRowResponse extends AuditableResponse {

    private UUID id;

    private String name;

    private String lastname;

    private String fullName;

    private String dni;

    private String sex;

    private String phone;

    /**
     * Sede donde la persona está actualmente activa (puede ser
     * distinta de la sede por la que apareció en el listado, si
     * ya se trasladó a otra).
     */
    private UUID activeBranchId;

    private String activeBranchName;

}
