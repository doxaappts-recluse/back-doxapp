package pe.dcs.app.features.user.shared;

import pe.dcs.app.entity.User;

public class BaseUserMapper {

    public static BaseUserSearchResponse base(User user) {
        BaseUserSearchResponse res = new BaseUserSearchResponse();
        res.setId(user.getId());
        res.setName(user.getName());
        res.setLastname(user.getLastname());
        return res;
    }
}