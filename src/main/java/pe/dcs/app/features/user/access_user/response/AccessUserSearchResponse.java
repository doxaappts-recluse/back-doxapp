package pe.dcs.app.features.user.access_user.response;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.features.user.shared.BaseUserSearchResponse;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class AccessUserSearchResponse {
    private UUID id;
    private String name;
    private String lastname;
    private Boolean hasCredential;
    private Boolean credentialActive;
    private String username;
    private List<AccessSummaryResponse> accesses;

}