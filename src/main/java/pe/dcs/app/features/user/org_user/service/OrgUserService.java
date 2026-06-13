package pe.dcs.app.features.user.org_user.service;

import pe.dcs.app.features.user.org_user.request.OrgUserCreateRequest;
import pe.dcs.app.features.user.org_user.request.OrgUserListRequest;
import pe.dcs.app.features.user.org_user.request.OrgUserUpdateRequest;
import pe.dcs.app.features.user.org_user.response.OrgUserCreateResponse;
import pe.dcs.app.features.user.org_user.response.OrgUserResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.UUID;

public interface OrgUserService {

    PageResponse<OrgUserResponse> search(OrgUserListRequest request);

    OrgUserResponse create(OrgUserCreateRequest request);

    OrgUserResponse update(UUID id, OrgUserUpdateRequest request);

    OrgUserCreateResponse getById(UUID id);
}