package pe.dcs.app.features.branch_transfer.service;

import pe.dcs.app.features.branch_transfer.request.BranchTransferHistoryRequest;
import pe.dcs.app.features.branch_transfer.request.BranchTransferRequest;
import pe.dcs.app.features.branch_transfer.request.BranchTransferSearchRequest;
import pe.dcs.app.features.branch_transfer.response.BranchTransferContextResponse;
import pe.dcs.app.features.branch_transfer.response.BranchTransferHistoryResponse;
import pe.dcs.app.features.branch_transfer.response.BranchTransferSearchRowResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.UUID;

public interface BranchTransferService {

    PageResponse<BranchTransferSearchRowResponse> search(BranchTransferSearchRequest request);

    BranchTransferContextResponse getCurrent(UUID personId);

    PageResponse<BranchTransferHistoryResponse> history(UUID personId, BranchTransferHistoryRequest request);

    void transfer(UUID personId, BranchTransferRequest request);
}
