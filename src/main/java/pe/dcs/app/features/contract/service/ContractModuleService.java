package pe.dcs.app.features.contract.service;

import pe.dcs.app.entity.Contract;
import pe.dcs.app.features.contract.request.ContractModuleRequest;
import pe.dcs.app.features.contract.response.ContractModuleResponse;

import java.util.List;
import java.util.UUID;

public interface ContractModuleService {

    List<ContractModuleResponse>
    saveModules(
            Contract contract,
            List<ContractModuleRequest> requests
    );

    List<ContractModuleResponse>
    getModules(UUID contractId);
}