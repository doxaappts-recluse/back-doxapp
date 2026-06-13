package pe.dcs.app.features.user.access_user.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.features.user.shared.BaseUserFilterRequest;

@Getter
@Setter
public class AccessUserFilterRequest extends BaseUserFilterRequest {

    private Boolean hasCredential;

    private Boolean credentialActive;

    private String username;

    private String role;
}