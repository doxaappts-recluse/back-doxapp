package pe.dcs.app.features.contract.mapper;

import pe.dcs.app.entity.Contract;
import pe.dcs.app.entity.ContractBranchLicense;
import pe.dcs.app.features.contract.response.ContractBranchLicenseResponse;
import pe.dcs.app.features.contract.response.ContractModuleConfigResponse;
import pe.dcs.app.features.contract.response.ContractResponse;
import pe.dcs.app.features.contract.response.ContractResponseSearch;
import pe.dcs.app.util.auditable.BaseMapper;

import java.util.List;

public class ContractMapper {

    private ContractMapper() {}

    public static ContractResponse toResponse(
            Contract contract,
            List<ContractModuleConfigResponse> modules,
            List<ContractBranchLicenseResponse> branchLicenses
    ) {

        ContractResponse dto = new ContractResponse();

        dto.setId(contract.getId());

        dto.setScope(contract.getScope());

        dto.setOrganizationId(
                contract.getOrganization().getId()
        );

        dto.setOrganizationName(
                contract.getOrganization().getName()
        );

        if (contract.getBranch() != null) {

            dto.setBranchId(contract.getBranch().getId());
            dto.setBranchName(contract.getBranch().getName());
        }

        dto.setPlanName(contract.getPlanName());
        dto.setPrice(contract.getPrice());
        dto.setCurrency(contract.getCurrency());
        dto.setStartDate(contract.getStartDate());
        dto.setEndDate(contract.getEndDate());
        dto.setMaxLicenses(contract.getMaxLicenses());
        dto.setDistributionMode(contract.getDistributionMode());
        dto.setStatus(contract.getStatus());
        dto.setRenewalType(contract.getRenewalType());

        dto.setPreviousContractId(
                contract.getPreviousContract() != null
                        ? contract.getPreviousContract().getId()
                        : null
        );

        dto.setModules(modules);
        dto.setBranchLicenses(branchLicenses);

        return dto;
    }

    public static ContractResponseSearch toResponseSearch(
            Contract contract,
            boolean showAudit
    ) {

        ContractResponseSearch response = new ContractResponseSearch();

        BaseMapper.mapAudit(contract, response, showAudit);

        response.setId(contract.getId());
        response.setScope(contract.getScope());

        response.setOrganizationId(
                contract.getOrganization().getId()
        );

        response.setOrganizationName(
                contract.getOrganization().getName()
        );

        if (contract.getBranch() != null) {

            response.setBranchId(contract.getBranch().getId());
            response.setBranchName(contract.getBranch().getName());
        }

        response.setPlanName(contract.getPlanName());
        response.setPrice(contract.getPrice());
        response.setCurrency(contract.getCurrency());
        response.setStartDate(contract.getStartDate());
        response.setEndDate(contract.getEndDate());
        response.setMaxLicenses(contract.getMaxLicenses());
        response.setDistributionMode(contract.getDistributionMode());
        response.setStatus(contract.getStatus());
        response.setRenewalType(contract.getRenewalType());

        return response;
    }

    public static ContractBranchLicenseResponse toBranchLicenseResponse(
            ContractBranchLicense license
    ) {

        return new ContractBranchLicenseResponse(
                license.getBranch().getId(),
                license.getBranch().getName(),
                license.getAllocatedLicenses()
        );
    }
}
