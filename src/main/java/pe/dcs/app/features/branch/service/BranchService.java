package pe.dcs.app.features.branch.service;

import pe.dcs.app.features.branch.request.BranchCreateRequest;
import pe.dcs.app.features.branch.request.BranchListRequest;
import pe.dcs.app.features.branch.request.BranchUpdateRequest;
import pe.dcs.app.features.branch.response.BranchListResponse;
import pe.dcs.app.features.branch.response.BranchResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.List;
import java.util.UUID;

public interface BranchService {

    PageResponse<BranchResponse> findByOrganization(
            UUID organizationId,
            BranchListRequest request
    );

    BranchResponse create(
            UUID organizationId,
            BranchCreateRequest request
    );

    BranchResponse update(
            UUID id,
            BranchUpdateRequest request
    );

    void enable(UUID id);

    void disable(UUID id);

    void changeMain(UUID id);

    List<BranchListResponse> findByOrganization(
            UUID organizationId
    );
}