package pe.dcs.app.features.user.org_user.mapper;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import pe.dcs.app.entity.*;
import pe.dcs.app.features.user.org_user.response.OrgAdminResponse;
import pe.dcs.app.util.enums.StatusType;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class OrgAdminMapper {

    /*public static OrgAdminResponse toResponse(Person user) {

        Credential credential = user.getCredential();

        UserAccess access =
                user.getAccesses()
                        .stream()
                        .filter(UserAccess::getActive)
                        .findFirst()
                        .orElse(null);

        Branch branch = user.getBranch();

        Organization organization =
                access != null
                        ? access.getOrganization()
                        : null;

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

                .status(
                        credential != null &&
                                credential.getStatus() == StatusType.ACTIVE
                )

                .organizationId(
                        organization != null
                                ? organization.getId()
                                : null
                )

                .organizationName(
                        organization != null
                                ? organization.getName()
                                : null
                )

                .branchId(
                        branch != null
                                ? branch.getId()
                                : null
                )

                .branchName(
                        branch != null
                                ? branch.getName()
                                : null
                )

                .branchMain(
                        branch != null
                                ? branch.getMain()
                                : null
                )

                .build();
    }
*/
}