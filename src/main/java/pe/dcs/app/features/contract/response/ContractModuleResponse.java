package pe.dcs.app.features.contract.response;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.features.permission.response.PermissionResponse;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class ContractModuleResponse {

    private UUID moduleId;

    private String moduleName;

    private String moduleCode;

    private List<PermissionResponse> permissions;
}