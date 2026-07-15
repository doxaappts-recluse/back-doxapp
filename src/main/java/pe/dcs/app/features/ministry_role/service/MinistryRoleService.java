package pe.dcs.app.features.ministry_role.service;

import pe.dcs.app.features.ministry_role.request.MinistryRoleRequest;
import pe.dcs.app.features.ministry_role.request.MinistryRoleSearchRequest;
import pe.dcs.app.features.ministry_role.response.MinistryRoleResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.List;
import java.util.UUID;

public interface MinistryRoleService {

    MinistryRoleResponse create(MinistryRoleRequest request);

    MinistryRoleResponse update(UUID id, MinistryRoleRequest request);

    void disable(UUID id);

    void enable(UUID id);

    PageResponse<MinistryRoleResponse> search(UUID id, MinistryRoleSearchRequest request);

    List<MinistryRoleResponse> findAll();

    MinistryRoleResponse getById(UUID id);

}