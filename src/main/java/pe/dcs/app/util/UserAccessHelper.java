package pe.dcs.app.util;

import pe.dcs.app.entity.User;
import pe.dcs.app.entity.UserAccess;

public class UserAccessHelper {

    private UserAccessHelper(){}

    public static UserAccess getAccessByRole(
            User user,
            String role
    ){
        return user.getAccesses()
                .stream()
                .filter(
                        a ->
                                a.getRole()!=null
                                        &&
                                        role.equals(
                                                a.getRole().getValue()
                                        )
                )
                .findFirst()
                .orElse(null);
    }

}