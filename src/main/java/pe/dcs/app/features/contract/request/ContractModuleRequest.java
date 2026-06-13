package pe.dcs.app.features.contract.request;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class ContractModuleRequest {

    private UUID moduleId;

    private List<UUID> permissionIds;
}