package pe.dcs.app.features.contract.service;

import pe.dcs.app.entity.Contract;
import pe.dcs.app.features.contract.request.ContractModuleRequest;
import pe.dcs.app.features.contract.response.ContractModuleConfigResponse;

import java.util.List;
import java.util.UUID;

public interface ContractModuleService {

    /**
     * Catálogo completo de módulos hoja activos + permisos
     * activos, marcando "assigned" con lo que ya tiene el
     * contrato (o todo en false si contractId es null, para
     * el formulario de creación).
     */
    List<ContractModuleConfigResponse> getCatalog(UUID contractId);

    /**
     * Reemplazo completo: borra lo que el contrato tenía
     * asignado y vuelve a crear según lo enviado.
     */
    void replaceModules(
            Contract contract,
            List<ContractModuleRequest> modules
    );
}
