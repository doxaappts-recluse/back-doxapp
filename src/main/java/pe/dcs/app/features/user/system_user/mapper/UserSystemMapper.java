package pe.dcs.app.features.user.system_user.mapper;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import pe.dcs.app.entity.Credential;
import pe.dcs.app.entity.Role;
import pe.dcs.app.entity.Person;
import pe.dcs.app.entity.UserAccess;
import pe.dcs.app.features.user.system_user.response.UserSystemResponse;
import pe.dcs.app.util.enums.StatusType;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class UserSystemMapper {

    public static UserSystemResponse toResponse(Person user) {

        Credential credential = user.getCredential();

        UserAccess access = user.getAccesses()
                .stream()
                .filter(
                        a -> a.getActive() == StatusType.ACTIVE
                )
                .findFirst()
                .orElse(null);

        Role role = access != null
                ? access.getRole()
                : null;

        return UserSystemResponse.builder()
                .id(user.getId())

                .name(user.getName())
                .lastname(user.getLastname())
                .dni(user.getDni())
                .sex(user.getSex())
                .phone(user.getPhone())
                .address(user.getAddress())

                .dateBirth(user.getDateBirth())
                .maritalStatus(user.getMaritalStatus())
                .children(user.getChildren())
                .dateAdmission(user.getDateAdmission())

                .username(
                        credential != null
                                ? credential.getUsername()
                                : null
                )

                .roleId(
                        role != null
                                ? role.getId()
                                : null
                )

                .roleName(
                        role != null
                                ? role.getName()
                                : null
                )

                .roleCode(
                        role != null
                                ? role.getValue()
                                : null
                )
                .status(
                        credential != null
                                ? credential.getStatus()
                                : null
                )

                .build();
    }

}