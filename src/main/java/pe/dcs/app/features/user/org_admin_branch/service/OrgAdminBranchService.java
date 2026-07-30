package pe.dcs.app.features.user.org_admin_branch.service;

import pe.dcs.app.features.user.org_admin_branch.request.OrgAdminBranchAddAccessRequest;
import pe.dcs.app.features.user.org_admin_branch.request.OrgAdminBranchCreateRequest;
import pe.dcs.app.features.user.org_admin_branch.request.OrgAdminBranchListRequest;
import pe.dcs.app.features.user.org_admin_branch.request.OrgAdminBranchUpdateRequest;
import pe.dcs.app.features.user.org_admin_branch.response.OrgAdminBranchDetailResponse;
import pe.dcs.app.features.user.org_admin_branch.response.OrgAdminBranchResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.UUID;

public interface OrgAdminBranchService {

    PageResponse<OrgAdminBranchResponse> search(OrgAdminBranchListRequest request);

    void create(OrgAdminBranchCreateRequest request);

    void update(UUID id, OrgAdminBranchUpdateRequest request);

    OrgAdminBranchDetailResponse findById(UUID id);

    void enable(UUID id);

    void disable(UUID id);

    void addAccess(UUID personId, OrgAdminBranchAddAccessRequest request);

    void enableAccess(UUID accessId);

    void disableAccess(UUID accessId);

}