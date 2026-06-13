package pe.dcs.app.features.user.ministry_user.response;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.features.user.shared.BaseUserSearchResponse;

@Getter
@Setter
public class MinistryUserSearchResponse extends BaseUserSearchResponse {

    private Boolean hasMinistry;
}