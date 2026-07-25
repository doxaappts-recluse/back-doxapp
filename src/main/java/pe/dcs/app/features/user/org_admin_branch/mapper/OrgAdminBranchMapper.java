package pe.dcs.app.features.user.org_admin_branch.mapper;

import org.springframework.stereotype.Component;
import pe.dcs.app.entity.Credential;
import pe.dcs.app.entity.Person;
import pe.dcs.app.entity.PersonBranch;
import pe.dcs.app.entity.UserAccess;
import pe.dcs.app.features.user.org_admin_branch.response.OrgAdminBranchDetailResponse;
import pe.dcs.app.features.user.org_admin_branch.response.OrgAdminBranchResponse;
import pe.dcs.app.util.UserAccessHelper;
import pe.dcs.app.util.enums.StatusType;

@Component
public class OrgAdminBranchMapper {

    public OrgAdminBranchResponse toResponse(Person person){

        UserAccess access =
                UserAccessHelper.getActiveAccess(person);

        Credential credential = person.getCredential();

        PersonBranch currentBranch =
                person.getBranchHistory()
                        .stream()
                        .filter(
                                pb -> pb.getStatus() == StatusType.ACTIVE
                        )
                        .findFirst()
                        .orElse(null);


        return OrgAdminBranchResponse
                .builder()
                .id(person.getId())
                .name(person.getName())
                .lastname(person.getLastname())
                .username(
                        credential != null
                                ? credential.getUsername()
                                : null
                )
                .hasCredential(credential != null)
                .credentialActive(
                        credential != null
                                &&
                                credential.getStatus() == StatusType.ACTIVE
                )

                /*
                 * ORGANIZACION
                 */
                .organizationId(
                        access != null &&
                                access.getOrganization()!=null
                                ? access.getOrganization().getId()
                                : null
                )
                .organizationName(
                        access != null &&
                                access.getOrganization()!=null
                                ? access.getOrganization().getName()
                                : null
                )

                /*
                 * SEDE ACTUAL PERSONA
                 */
                .branchId(
                        currentBranch != null
                                ? currentBranch.getBranch().getId()
                                : null
                )
                .branchName(
                        currentBranch != null
                                ? currentBranch.getBranch().getName()
                                : null
                )
                .branchMain(
                        currentBranch != null
                                ? currentBranch.getBranch().getMain()
                                : null
                )

                .build();
    }

    public OrgAdminBranchDetailResponse toDetailResponse(Person person) {

        Credential credential = person.getCredential();

        OrgAdminBranchDetailResponse response = new OrgAdminBranchDetailResponse();

        response.setId(person.getId());
        response.setName(person.getName());
        response.setLastname(person.getLastname());
        response.setSex(person.getSex());
        response.setPhone(person.getPhone());
        response.setDni(person.getDni());
        response.setMaritalStatus(person.getMaritalStatus());
        response.setChildren(person.getChildren());
        response.setAddress(person.getAddress());
        response.setDateBirth(person.getDateBirth());
        response.setUsername(
                credential != null
                        ? credential.getUsername()
                        : null
        );

        return response;

    }

}