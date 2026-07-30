package pe.dcs.app.features.user.org_user.service;

import pe.dcs.app.features.user.org_user.request.OrgUserCreateRequest;
import pe.dcs.app.features.user.org_user.request.OrgUserSearchRequest;
import pe.dcs.app.features.user.org_user.request.OrgUserUpdateRequest;
import pe.dcs.app.features.user.org_user.response.OrgUserResponse;
import pe.dcs.app.features.user.org_user.response.OrgUserSearchRowResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.UUID;

/**
 * Crea/edita/lista personas dentro de la organización/sede del
 * contexto de quien hace la petición (ORG_ADMIN / ORG_BRANCH_ADMIN).
 * Solo crea Person + PersonBranch (sede del contexto); no maneja
 * credenciales ni acceso al sistema.
 */
public interface OrgUserService {

    PageResponse<OrgUserSearchRowResponse> search(OrgUserSearchRequest request);

    OrgUserResponse create(OrgUserCreateRequest request);

    OrgUserResponse update(UUID id, OrgUserUpdateRequest request);

    OrgUserResponse getById(UUID id);

}
