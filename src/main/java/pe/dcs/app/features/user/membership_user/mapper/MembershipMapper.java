package pe.dcs.app.features.user.membership_user.mapper;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import pe.dcs.app.entity.Membership;
import pe.dcs.app.entity.User;
import pe.dcs.app.features.user.membership_user.response.MembershipContextResponse;
import pe.dcs.app.features.user.membership_user.response.MembershipResponse;
import pe.dcs.app.features.user.membership_user.response.MembershipUserResponse;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MembershipMapper {

    public static MembershipResponse toResponse(Membership membership) {

        return new MembershipResponse(
                membership.getId(),
                membership.getStartDate(),
                membership.getEndDate(),
                membership.getStatus(),
                membership.getExitReason(),
                membership.getReason(),
                membership.getCurrent(),
                membership.getNotes()
        );
    }

    public static MembershipContextResponse toResponseWithUser(Membership membership) {

        User user = membership.getUser();

        return new MembershipContextResponse(

                new MembershipUserResponse(
                        user.getId(),
                        user.getName(),
                        user.getLastname()
                ),

                MembershipMapper.toResponse(membership)
        );
    }

}
