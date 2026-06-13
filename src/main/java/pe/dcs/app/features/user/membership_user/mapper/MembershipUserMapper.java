package pe.dcs.app.features.user.membership_user.mapper;

import pe.dcs.app.entity.Membership;
import pe.dcs.app.entity.User;
import pe.dcs.app.features.user.membership_user.response.MembershipUserSearchResponse;
import pe.dcs.app.features.user.shared.BaseUserMapper;
import pe.dcs.app.features.user.shared.BaseUserSearchResponse;

import java.util.Comparator;
import java.util.Optional;

public class MembershipUserMapper {

    public static MembershipUserSearchResponse map(User user) {

        MembershipUserSearchResponse res = new MembershipUserSearchResponse();

        BaseUserSearchResponse base = BaseUserMapper.base(user);

        res.setId(base.getId());
        res.setName(base.getName());
        res.setLastname(base.getLastname());

        Optional<Membership> last = user.getMemberships()
                .stream()
                .max(Comparator.comparing(Membership::getStartDate));

        if (last.isPresent()) {
            Membership m = last.get();

            res.setHasMembership(true);
            res.setMembershipStartDate(m.getStartDate());
            res.setMembershipEndDate(m.getEndDate());
            res.setMembershipStatus(m.getStatus().name());
            res.setMembershipReason(m.getReason());
            res.setMembershipExitReason(m.getExitReason());
        } else {
            res.setHasMembership(false);
        }

        return res;
    }
}