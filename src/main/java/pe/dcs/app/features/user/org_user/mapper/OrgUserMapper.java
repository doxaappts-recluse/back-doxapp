package pe.dcs.app.features.user.org_user.mapper;

import pe.dcs.app.entity.User;
import pe.dcs.app.features.user.org_user.response.OrgUserResponse;
import pe.dcs.app.features.user.shared.BaseUserMapper;

public class OrgUserMapper {

    public static OrgUserResponse toResponse(User user) {

        OrgUserResponse res = new OrgUserResponse();

        var base = BaseUserMapper.base(user);

        res.setId(base.getId());
        res.setName(base.getName());
        res.setLastname(base.getLastname());
        res.setDni(user.getDni());
        res.setSex(user.getSex());
        res.setPhone(user.getPhone());
        res.setDateBirth(user.getDateBirth());

        return res;
    }
}