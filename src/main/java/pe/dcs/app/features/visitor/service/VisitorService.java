package pe.dcs.app.features.visitor.service;

import pe.dcs.app.features.visitor.request.VisitorConvertToMemberRequest;
import pe.dcs.app.features.visitor.request.VisitorFormRequest;
import pe.dcs.app.features.visitor.request.VisitorSearchRequest;
import pe.dcs.app.features.visitor.response.VisitorDetailResponse;
import pe.dcs.app.features.visitor.response.VisitorSearchRowResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.UUID;

public interface VisitorService {

    PageResponse<VisitorSearchRowResponse> search(VisitorSearchRequest request);

    VisitorDetailResponse getByPersonId(UUID personId);

    void create(UUID personId, VisitorFormRequest request);

    void update(UUID personId, VisitorFormRequest request);

    void convertToMember(UUID personId, VisitorConvertToMemberRequest request);
}
