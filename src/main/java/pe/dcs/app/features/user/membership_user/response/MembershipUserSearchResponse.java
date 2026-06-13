package pe.dcs.app.features.user.membership_user.response;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.features.user.shared.BaseUserSearchResponse;
import pe.dcs.app.util.enums.membership.MembershipExitReason;

import java.time.LocalDate;

@Getter
@Setter
public class MembershipUserSearchResponse extends BaseUserSearchResponse {

    private Boolean hasMembership;
    private LocalDate membershipStartDate;
    private LocalDate membershipEndDate;
    private String membershipStatus;
    private String membershipReason;
    private MembershipExitReason membershipExitReason;
}