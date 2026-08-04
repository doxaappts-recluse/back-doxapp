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

    private String code;

    private String name;

    /** Id del módulo padre (null si el módulo hoja no tiene padre — caso legado/standalone). */
    private UUID parentId;

    /** Nombre del módulo padre, para agrupar el catálogo en el UI de asignación de contratos. */
    private String parentName;

    private boolean assigned;

    private List<ContractPermissionConfigResponse> permissions;
}
