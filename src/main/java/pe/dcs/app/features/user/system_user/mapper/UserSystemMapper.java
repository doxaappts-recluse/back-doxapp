package pe.dcs.app.features.user.system_user.mapper;

import pe.dcs.app.entity.Credential;
import pe.dcs.app.entity.User;
import pe.dcs.app.features.user.system_user.response.UserSystemResponse;
import pe.dcs.app.features.user.org_user.response.OrgAdminResponse;
import pe.dcs.app.util.enums.StatusType;

public class UserSystemMapper {

    private UserSystemMapper() {}

    public static UserSystemResponse toResponse(User user) {

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

                .roleId(
                        user.getRole() != null
                                ? user.getRole().getId()
                                : null
                )

                .roleName(
                        user.getRole() != null
                                ? user.getRole().getName()
                                : null
                )

                .roleCode(
                        user.getRole() != null
                                ? user.getRole().getValue()
                                : null
                )

                .username(
                        user.getCredential() != null
                                ? user.getCredential().getUsername()
                                : null
                )

                .status(
                        user.getCredential() != null
                                && user.getCredential().getStatus() == StatusType.ACTIVE
                )

                .build();
    }

    public static OrgAdminResponse mapToOrgAdminResponse(User user) {

        Credential credential = user.getCredential();

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
                        user.getOrganization() != null
                                ? user.getOrganization().getId()
                                : null
                )
                .build();
    }
}