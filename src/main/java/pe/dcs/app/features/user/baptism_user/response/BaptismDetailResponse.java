package pe.dcs.app.features.user.baptism_user.response;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.features.user.shared.BaseUserSearchResponse;

@Getter
@Setter
public class BaptismDetailResponse {

    private BaseUserSearchResponse user;

    private BaptismData baptism;

}