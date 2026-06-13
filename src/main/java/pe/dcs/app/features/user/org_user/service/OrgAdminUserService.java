package pe.dcs.app.features.user.org_user.service;

import pe.dcs.app.features.user.org_user.request.OrgAdminCreateRequest;
import pe.dcs.app.features.user.org_user.request.OrgAdminUpdateRequest;
import pe.dcs.app.features.user.org_user.response.OrgAdminResponse;

import java.util.UUID;

public interface OrgAdminUserService {
    OrgAdminResponse createOrgAdmin(OrgAdminCreateRequest request);

    OrgAdminResponse getOrgAdmin(UUID id);

    OrgAdminResponse updateOrgAdmin(UUID id, OrgAdminUpdateRequest request);
}
