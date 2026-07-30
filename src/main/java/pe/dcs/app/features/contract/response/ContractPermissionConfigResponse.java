package pe.dcs.app.features.contract.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
public class ContractPermissionConfigResponse {

    private UUID id;

    private String code;

    private String name;

    private boolean assigned;
}
