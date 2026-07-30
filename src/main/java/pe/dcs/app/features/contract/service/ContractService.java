package pe.dcs.app.features.contract.service;

import pe.dcs.app.features.contract.request.ContractCreateRequest;
import pe.dcs.app.features.contract.request.ContractListRequest;
import pe.dcs.app.features.contract.request.ContractUpdateRequest;
import pe.dcs.app.features.contract.response.ContractResponse;
import pe.dcs.app.features.contract.response.ContractResponseSearch;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.UUID;

public interface ContractService {

    PageResponse<ContractResponseSearch> search(
            ContractListRequest request
    );

    ContractResponse getById(UUID id);

    ContractResponse create(
            ContractCreateRequest request
    );

    ContractResponse update(
            UUID id,
            ContractUpdateRequest request
    );

    PageResponse<ContractResponseSearch> historyByOrganization(
            UUID organizationId,
            ContractListRequest request
    );

    PageResponse<ContractResponseSearch> historyByBranch(
            UUID branchId,
            ContractListRequest request
    );

    void activate(UUID id);

    void reactivate(UUID id);

    void suspend(UUID id);

    void cancel(UUID id);

}
