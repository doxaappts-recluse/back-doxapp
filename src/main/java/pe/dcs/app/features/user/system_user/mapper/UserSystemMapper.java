package pe.dcs.app.features.user.system_user.mapper;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import pe.dcs.app.entity.Credential;
import pe.dcs.app.entity.Role;
import pe.dcs.app.entity.User;
import pe.dcs.app.entity.UserAccess;
import pe.dcs.app.features.user.org_user.response.OrgAdminResponse;
import pe.dcs.app.features.user.system_user.response.UserSystemResponse;
import pe.dcs.app.util.enums.StatusType;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class UserSystemMapper {


    public static UserSystemResponse toResponse(User user) {

        UserAccess access = getActiveAccess(user);

        Role role =
                access != null
                        ? access.getRole()
                        : null;

        Credential credential =
                user.getCredential();

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
                // ROLE FROM ACCESS
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
                // CREDENTIAL
                .username(
                        credential != null
                                ? credential.getUsername()
                                : null
                )
                .status(
                        credential != null
                                &&
                                credential.getStatus()
                                        == StatusType.ACTIVE
                )
                .build();
    }



    public static OrgAdminResponse mapToOrgAdminResponse(User user) {

        Credential credential = user.getCredential();

        UserAccess access =
                user.getAccesses()
                        .stream()
                        .filter(UserSystemMapper::isOrgAdmin)
                        .findFirst()
                        .orElse(null);


        return OrgAdminResponse.builder()
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
                .organizationId(
                        access != null
                                ?
                                access.getOrganization().getId()
                                :
                                null
                )
                .build();
    }

    private static UserAccess getActiveAccess(User user) {

        if(user.getAccesses() == null)
            return null;

        return user.getAccesses()
                .stream()
                .filter(UserAccess::getActive)
                .findFirst()
                .orElse(null);
    }

    private static boolean isOrgAdmin(UserAccess access) {
        return access.getRole() != null
                &&
                "ORG_ADMIN"
                        .equals(access.getRole().getValue());
    }

}