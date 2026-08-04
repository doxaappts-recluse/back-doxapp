package pe.dcs.app.features.branch_transfer.mapper;

import org.springframework.stereotype.Component;
import pe.dcs.app.entity.Person;
import pe.dcs.app.entity.PersonBranch;
import pe.dcs.app.features.branch_transfer.response.BranchTransferContextResponse;
import pe.dcs.app.features.branch_transfer.response.BranchTransferHistoryResponse;
import pe.dcs.app.features.branch_transfer.response.BranchTransferSearchRowResponse;
import pe.dcs.app.features.branch_transfer.response.BranchTransferUserResponse;

@Component
public class BranchTransferMapper {

    public BranchTransferSearchRowResponse toSearchRow(Person person, PersonBranch current) {

        BranchTransferSearchRowResponse row = new BranchTransferSearchRowResponse();

        row.setId(person.getId());
        row.setName(person.getName());
        row.setLastname(person.getLastname());

        if (current != null) {
            row.setCurrentBranchName(current.getBranch().getName());
            row.setCurrentBranchStartDate(current.getStartDate());
        }

        return row;
    }

    public BranchTransferHistoryResponse toHistoryResponse(PersonBranch personBranch) {

        BranchTransferHistoryResponse response = new BranchTransferHistoryResponse();

        response.setId(personBranch.getId());
        response.setBranchId(personBranch.getBranch().getId());
        response.setBranchName(personBranch.getBranch().getName());

        response.setStatus(
                personBranch.getStatus() != null
                        ? personBranch.getStatus().name()
                        : null
        );

        response.setStartDate(personBranch.getStartDate());
        response.setEndDate(personBranch.getEndDate());
        response.setTransferReason(personBranch.getTransferReason());

        return response;
    }

    public BranchTransferUserResponse toUserResponse(Person person, PersonBranch current) {

        BranchTransferUserResponse response = new BranchTransferUserResponse();

        response.setId(person.getId());
        response.setName(person.getName());
        response.setLastname(person.getLastname());

        response.setOrganizationId(
                current != null
                        ? current.getBranch().getOrganization().getId()
                        : null
        );

        return response;
    }

    public BranchTransferContextResponse toContextResponse(Person person, PersonBranch current) {

        BranchTransferContextResponse response = new BranchTransferContextResponse();

        response.setUser(toUserResponse(person, current));

        response.setCurrentBranch(
                current != null
                        ? toHistoryResponse(current)
                        : null
        );

        return response;
    }
}
