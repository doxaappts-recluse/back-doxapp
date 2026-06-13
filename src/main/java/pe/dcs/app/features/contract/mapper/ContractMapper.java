package pe.dcs.app.features.contract.mapper;

import pe.dcs.app.entity.Contract;
import pe.dcs.app.features.contract.response.ContractModuleResponse;
import pe.dcs.app.features.contract.response.ContractResponse;
import pe.dcs.app.features.contract.response.ContractResponseSearch;

import java.util.List;

public class ContractMapper {

    private ContractMapper(){}

    public static ContractResponse toResponse(
            Contract contract,
            List<ContractModuleResponse> modules
    ) {

        ContractResponse dto = new ContractResponse();

        dto.setId(contract.getId());

        dto.setOrganizationId(
                contract.getOrganization().getId()
        );

        dto.setOrganizationName(
                contract.getOrganization().getName()
        );

        dto.setPlanName(contract.getPlanName());

        dto.setPrice(contract.getPrice());

        dto.setCurrency(contract.getCurrency());

        dto.setStartDate(contract.getStartDate());

        dto.setEndDate(contract.getEndDate());

        dto.setNumberUsers(contract.getNumberUsers());

        dto.setStatus(
                contract.getStatus().name()
        );

        dto.setRenewalType(
                contract.getRenewalType()
        );

        dto.setPreviousContractId(
                contract.getPreviousContractId()
        );

        dto.setModules(modules);

        return dto;
    }

    public static ContractResponseSearch toResponseSearch(
            Contract contract
    ) {

        ContractResponseSearch response =
                new ContractResponseSearch();

        response.setId(contract.getId());

        response.setOrganizationId(
                contract.getOrganization().getId()
        );

        response.setOrganizationName(
                contract.getOrganization().getName()
        );

        response.setPlanName(contract.getPlanName());

        response.setPrice(contract.getPrice());

        response.setCurrency(contract.getCurrency());

        response.setStartDate(contract.getStartDate());

        response.setEndDate(contract.getEndDate());

        response.setNumberUsers(
                contract.getNumberUsers()
        );

        response.setStatus(contract.getStatus());

        response.setRenewalType(
                contract.getRenewalType()
        );

        return response;
    }
}