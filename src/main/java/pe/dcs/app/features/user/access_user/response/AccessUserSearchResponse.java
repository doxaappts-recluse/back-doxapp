package pe.dcs.app.features.user.access_user.response;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.features.user.shared.BaseUserSearchResponse;

@Getter
@Setter
public class AccessUserSearchResponse extends BaseUserSearchResponse {

    private Boolean hasCredential;
    private Boolean credentialActive;
    private String username;
    private String role;
}