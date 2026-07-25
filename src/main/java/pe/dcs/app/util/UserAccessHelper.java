package pe.dcs.app.util;

import pe.dcs.app.entity.Person;
import pe.dcs.app.entity.UserAccess;
import pe.dcs.app.util.enums.RoleType;
import pe.dcs.app.util.enums.StatusType;

import java.util.UUID;

public class UserAccessHelper {

    private UserAccessHelper(){}

    public static UserAccess getAccessByRole(Person person, RoleType role){

        return person.getAccesses()
                .stream()
                .filter(
                        a ->
                                a.getRole() != null
                                        &&
                                        a.getRole()
                                                .getValue()
                                                == role
                )
                .findFirst()
                .orElse(null);
    }

    public static UserAccess getActiveAccess(Person person){

        return person.getAccesses()
                .stream()
                .filter(
                        a ->
                                a.getActive()
                                        == StatusType.ACTIVE
                )
                .findFirst()
                .orElse(null);
    }

    public static UserAccess getActiveAccess(Person person, UUID organizationId){

        return person.getAccesses()
                .stream()
                .filter(
                        a ->
                                a.getActive()
                                        == StatusType.ACTIVE
                )
                .filter(
                        a ->
                                a.getOrganization() != null
                                        &&
                                        a.getOrganization()
                                                .getId()
                                                .equals(
                                                        organizationId
                                                )
                )
                .findFirst()
                .orElse(null);
    }

}