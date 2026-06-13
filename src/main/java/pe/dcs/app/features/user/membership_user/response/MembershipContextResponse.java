package pe.dcs.app.features.user.membership_user.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class MembershipContextResponse {

    private MembershipUserResponse user;

    private MembershipResponse currentMembership;
}