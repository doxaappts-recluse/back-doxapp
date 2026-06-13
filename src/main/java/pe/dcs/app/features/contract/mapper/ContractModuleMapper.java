package pe.dcs.app.features.contract.mapper;

import pe.dcs.app.entity.ContractModule;
import pe.dcs.app.features.contract.response.ContractModuleResponse;
import pe.dcs.app.features.permission.response.PermissionResponse;

import java.util.List;

public class ContractModuleMapper {

    private ContractModuleMapper(){}

    public static ContractModuleResponse toResponse(
            ContractModule contractModule,
            List<PermissionResponse> permissions
    ) {

        ContractModuleResponse dto =
                new ContractModuleResponse();

        dto.setModuleId(
                contractModule.getModule().getId()
        );

        dto.setModuleName(
                contractModule.getModule().getName()
        );

        dto.setModuleCode(
                contractModule.getModule().getCode()
        );

        dto.setPermissions(permissions);

        return dto;
    }
}