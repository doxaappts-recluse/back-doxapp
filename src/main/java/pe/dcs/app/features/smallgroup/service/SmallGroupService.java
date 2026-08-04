package pe.dcs.app.features.smallgroup.service;

import pe.dcs.app.features.smallgroup.request.SmallGroupFormRequest;
import pe.dcs.app.features.smallgroup.request.SmallGroupMemberFormRequest;
import pe.dcs.app.features.smallgroup.request.SmallGroupSearchRequest;
import pe.dcs.app.features.smallgroup.response.SmallGroupDetailResponse;
import pe.dcs.app.features.smallgroup.response.SmallGroupMemberResponse;
import pe.dcs.app.features.smallgroup.response.SmallGroupPersonSearchResponse;
import pe.dcs.app.features.smallgroup.response.SmallGroupSearchRowResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.List;
import java.util.UUID;

public interface SmallGroupService {

    PageResponse<SmallGroupSearchRowResponse> search(SmallGroupSearchRequest request);

    SmallGroupDetailResponse getById(UUID id);

    SmallGroupPersonSearchResponse findPersonByDni(String dni);

    UUID create(SmallGroupFormRequest request);

    void update(UUID id, SmallGroupFormRequest request);

    List<SmallGroupMemberResponse> listMembers(UUID groupId);

    void addMember(UUID groupId, SmallGroupMemberFormRequest request);

    void removeMember(UUID groupId, UUID memberId);
}
