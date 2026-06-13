package pe.dcs.app.features.user.baptism_user.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.features.user.shared.BaseUserFilterRequest;

import java.util.UUID;

@Getter
@Setter
public class BaptismFilter extends BaseUserFilterRequest {

    private Boolean verified;

}