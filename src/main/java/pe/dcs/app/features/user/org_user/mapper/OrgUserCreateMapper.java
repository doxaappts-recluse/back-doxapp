package pe.dcs.app.features.user.org_user.mapper;

import pe.dcs.app.entity.Person;
import pe.dcs.app.features.user.org_user.response.OrgUserCreateResponse;
import pe.dcs.app.features.user.shared.BaseUserMapper;

public class OrgUserCreateMapper {

    public static OrgUserCreateResponse toResponse(Person user) {

        OrgUserCreateResponse res = new OrgUserCreateResponse();

        var base = BaseUserMapper.base(user);

        res.setId(base.getId());
        res.setName(base.getName());
        res.setLastname(base.getLastname());

        res.setDni(user.getDni());
        res.setSex(user.getSex());
        res.setPhone(user.getPhone());
        res.setAddress(user.getAddress());
        res.setDateAdmission(user.getDateAdmission());
        res.setMaritalStatus(user.getMaritalStatus());
        res.setChildren(user.getChildren());
        res.setDateBirth(user.getDateBirth());

        return res;
    }
}
