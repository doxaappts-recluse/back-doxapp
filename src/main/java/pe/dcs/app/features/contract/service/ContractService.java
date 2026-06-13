package pe.dcs.app.features.contract.service;

import pe.dcs.app.features.contract.request.ContractCreateRequest;
import pe.dcs.app.features.contract.request.ContractListRequest;
import pe.dcs.app.features.contract.response.ContractResponse;
import pe.dcs.app.features.contract.response.ContractResponseSearch;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.UUID;

public interface ContractService {

    PageResponse<ContractResponseSearch>
    search(ContractListRequest request);

    PageResponse<ContractResponseSearch>
    history(
            UUID organizationId,
            ContractListRequest request
    );

    ContractResponse getBaseContract(UUID organizationId);

    void process(
            ContractCreateRequest request
    );

    void  suspend(UUID id);

    void  reactivate(UUID id);

    void  cancel(UUID id);

    void  activate(UUID id);


}