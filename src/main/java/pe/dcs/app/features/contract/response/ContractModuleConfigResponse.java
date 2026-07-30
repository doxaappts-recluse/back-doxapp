package pe.dcs.app.features.contract.response;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

/**
 * Catálogo de módulos hoja (sin submódulos propios) con sus
 * permisos, marcando "assigned" en lo que el contrato ya
 * tiene habilitado. Igual para crear (todo en false) o
 * editar (con lo ya guardado marcado).
 */
@Getter
@Setter
public class ContractModuleConfigResponse {

    private UUID moduleId;

    private String name;

    private boolean assigned;

    private List<ContractPermissionConfigResponse> permissions;
}
