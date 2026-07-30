package pe.dcs.app.features.user.org_user.mapper;

import org.springframework.stereotype.Component;
import pe.dcs.app.entity.Person;
import pe.dcs.app.entity.PersonBranch;
import pe.dcs.app.features.user.org_user.response.OrgUserResponse;
import pe.dcs.app.features.user.org_user.response.OrgUserSearchRowResponse;
import pe.dcs.app.util.auditable.BaseMapper;
import pe.dcs.app.util.enums.StatusType;

@Component
public class OrgUserMapper {

    public OrgUserSearchRowResponse toSearchRow(Person person, boolean showAudit) {

        OrgUserSearchRowResponse row = new OrgUserSearchRowResponse();

        BaseMapper.mapAudit(person, row, showAudit);

        PersonBranch activeBranch = findActiveBranch(person);

        row.setId(person.getId());
        row.setName(person.getName());
        row.setLastname(person.getLastname());
        row.setFullName(fullName(person));
        row.setDni(person.getDni());
        row.setSex(person.getSex());
        row.setPhone(person.getPhone());

        row.setActiveBranchId(
                activeBranch != null ? activeBranch.getBranch().getId() : null
        );

        row.setActiveBranchName(
                activeBranch != null ? activeBranch.getBranch().getName() : null
        );

        return row;
    }

    public OrgUserResponse toResponse(Person person) {

        OrgUserResponse response = new OrgUserResponse();

        PersonBranch activeBranch = findActiveBranch(person);

        response.setId(person.getId());
        response.setName(person.getName());
        response.setLastname(person.getLastname());
        response.setFullName(fullName(person));
        response.setDni(person.getDni());
        response.setSex(person.getSex());
        response.setPhone(person.getPhone());
        response.setAddress(person.getAddress());
        response.setDateBirth(person.getDateBirth());

        response.setMaritalStatus(
                person.getMaritalStatus() != null
                        ? person.getMaritalStatus().name()
                        : null
        );

        response.setChildren(person.getChildren());
        response.setDateAdmission(person.getDateAdmission());

        response.setActiveBranchId(
                activeBranch != null ? activeBranch.getBranch().getId() : null
        );

        response.setActiveBranchName(
                activeBranch != null ? activeBranch.getBranch().getName() : null
        );

        return response;
    }

    private PersonBranch findActiveBranch(Person person) {

        return person.getBranchHistory()
                .stream()
                .filter(pb -> pb.getStatus() == StatusType.ACTIVE)
                .findFirst()
                .orElse(null);
    }

    private String fullName(Person person) {

        String name = person.getName() != null ? person.getName() : "";
        String lastname = person.getLastname() != null ? person.getLastname() : "";

        return (name + " " + lastname).trim();
    }

}
