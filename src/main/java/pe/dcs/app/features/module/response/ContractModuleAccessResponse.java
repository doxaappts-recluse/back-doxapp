package pe.dcs.app.features.module.response;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Módulo HIJO habilitado por el contrato activo
 * de la organización/sede, junto con los permisos
 * que ese contrato permite para el módulo.
 *
 * Ya viene filtrado:
 * - Solo módulos hijos (parent != null).
 * - Solo módulos/permisos ACTIVOS.
 * - Solo lo que el contrato activo habilita.
 */
@Getter
@Setter
public class ContractModuleAccessResponse {

    private UUID moduleId;
    private String name;

    private List<ContractModulePermissionOptionResponse> permissions =
            new ArrayList<>();

}
