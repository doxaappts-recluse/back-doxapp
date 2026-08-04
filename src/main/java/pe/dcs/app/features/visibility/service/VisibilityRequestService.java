package pe.dcs.app.features.visibility.service;

import pe.dcs.app.features.visibility.request.VisibilityRequestApproveRequest;
import pe.dcs.app.features.visibility.request.VisibilityRequestCreateRequest;
import pe.dcs.app.features.visibility.request.VisibilityRequestSearchRequest;
import pe.dcs.app.features.visibility.response.PersonBranchOptionResponse;
import pe.dcs.app.features.visibility.response.VisibilityRequestPersonResponse;
import pe.dcs.app.features.visibility.response.VisibilityRequestRowResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.List;
import java.util.UUID;

public interface VisibilityRequestService {

    PageResponse<VisibilityRequestRowResponse> searchIncoming(VisibilityRequestSearchRequest request);

    PageResponse<VisibilityRequestRowResponse> searchOutgoing(VisibilityRequestSearchRequest request);

    VisibilityRequestPersonResponse findPersonByDni(String dni);

    List<PersonBranchOptionResponse> getPersonBranches(UUID personId);

    void create(UUID personId, VisibilityRequestCreateRequest request);

    void approve(UUID requestId, VisibilityRequestApproveRequest request);

    void reject(UUID requestId);
}
