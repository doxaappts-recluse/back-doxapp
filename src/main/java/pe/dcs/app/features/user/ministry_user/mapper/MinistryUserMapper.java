package pe.dcs.app.features.user.ministry_user.mapper;

import pe.dcs.app.entity.User;
import pe.dcs.app.features.user.ministry_user.response.MinistryUserSearchResponse;
import pe.dcs.app.features.user.shared.BaseUserMapper;
import pe.dcs.app.features.user.shared.BaseUserSearchResponse;

public class MinistryUserMapper {

    public static MinistryUserSearchResponse map(User user) {

        MinistryUserSearchResponse res =
                new MinistryUserSearchResponse();

        BaseUserSearchResponse base =
                BaseUserMapper.base(user);

        res.setId(base.getId());
        res.setName(base.getName());
        res.setLastname(base.getLastname());

        res.setHasMinistry(
                user.getMemberMinistryAssignments() != null
                        && !user.getMemberMinistryAssignments().isEmpty()
        );

        return res;
    }
}