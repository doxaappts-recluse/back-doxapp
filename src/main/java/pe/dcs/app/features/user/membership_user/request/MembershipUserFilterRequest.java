package pe.dcs.app.features.user.membership_user.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.features.user.shared.BaseUserFilterRequest;

@Getter
@Setter
public class MembershipUserFilterRequest extends BaseUserFilterRequest {

    private Boolean hasMembership;

    private String membershipStatus;
}