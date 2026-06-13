package pe.dcs.app.features.user.ministry_user.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.features.user.shared.BaseUserFilterRequest;

@Getter
@Setter
public class MinistryUserFilterRequest extends BaseUserFilterRequest {

    private Boolean hasMinistry;
}