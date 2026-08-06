package pe.dcs.app.features.user.org_admin_branch.mapper;

import org.springframework.stereotype.Component;
import pe.dcs.app.entity.Credential;
import pe.dcs.app.entity.Person;
import pe.dcs.app.entity.PersonBranch;
import pe.dcs.app.entity.UserAccess;
import pe.dcs.app.features.user.org_admin_branch.response.OrgAdminBranchDetailResponse;
import pe.dcs.app.features.user.org_admin_branch.response.OrgAdminBranchResponse;
import pe.dcs.app.features.user.org_admin_branch.response.UserAccessResponse;
import pe.dcs.app.util.UserAccessHelper;
import pe.dcs.app.util.auditable.BaseMapper;
import pe.dcs.app.util.enums.StatusType;

import java.util.Comparator;

@Component
public class OrgAdminBranchMapper {

    public OrgAdminBranchResponse toResponse(Person person, boolean showAudit){

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


        OrgAdminBranchResponse response = OrgAdminBranchResponse
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

                /*
                 * ROL
                 */
                .roleId(
                        access != null &&
                                access.getRole() != null
                                ? access.getRole().getId()
                                : null
                )
                .roleName(
                        access != null &&
                                access.getRole() != null
                                ? access.getRole().getName()
                                : null
                )

                .build();

        BaseMapper.mapAudit(person, response, showAudit);

        return response;
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

        response.setAccesses(
                person.getAccesses()
                        .stream()
                        .sorted(
                                Comparator.comparing(
                                        (UserAccess a) ->
                                                a.getActive() == StatusType.ACTIVE ? 0 : 1
                                )
                        )
                        .map(this::toAccessResponse)
                        .toList()
        );

        return response;

    }

    public UserAccessResponse toAccessResponse(UserAccess access) {

        UserAccessResponse response = new UserAccessResponse();

        response.setId(access.getId());

        response.setOrganizationId(
                access.getOrganization() != null
                        ? access.getOrganization().getId()
                        : null
        );

        response.setOrganizationName(
                access.getOrganization() != null
                        ? access.getOrganization().getName()
                        : null
        );

        response.setBranchId(
                access.getBranch() != null
                        ? access.getBranch().getId()
                        : null
        );

        response.setBranchName(
                access.getBranch() != null
                        ? access.getBranch().getName()
                        : null
        );

        response.setRoleId(
                access.getRole() != null
                        ? access.getRole().getId()
                        : null
        );

        response.setRoleName(
                access.getRole() != null
                        ? access.getRole().getName()
                        : null
        );

        response.setRoleCode(
                access.getRole() != null && access.getRole().getValue() != null
                        ? access.getRole().getValue().name()
                        : null
        );

        response.setActive(access.getActive() == StatusType.ACTIVE);

        return response;
    }

}