package pe.dcs.app.features.baptism_user.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.features.user.shared.BaseUserFilterRequest;

@Getter
@Setter
public class BaptismFilter extends BaseUserFilterRequest {

    private Boolean verified;

}