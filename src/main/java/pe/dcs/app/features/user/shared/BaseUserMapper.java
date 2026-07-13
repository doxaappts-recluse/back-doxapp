package pe.dcs.app.features.user.shared;

import pe.dcs.app.entity.Person;

public class BaseUserMapper {

    public static BaseUserSearchResponse base(Person user) {
        BaseUserSearchResponse res = new BaseUserSearchResponse();
        res.setId(user.getId());
        res.setName(user.getName());
        res.setLastname(user.getLastname());
        return res;
    }
}