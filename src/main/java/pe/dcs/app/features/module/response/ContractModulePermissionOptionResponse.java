package pe.dcs.app.features.module.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Permiso disponible para un módulo, según el
 * contrato activo de la organización/sede.
 */
@Getter
@Setter
@AllArgsConstructor
public class ContractModulePermissionOptionResponse {

    private UUID id;
    private String code;
    private String name;

}
