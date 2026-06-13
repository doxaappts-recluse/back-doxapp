package pe.dcs.app.features.organization.service;

import pe.dcs.app.features.organization.request.OrganizationCreateRequest;
import pe.dcs.app.features.organization.request.OrganizationListRequest;
import pe.dcs.app.features.organization.request.OrganizationUpdateRequest;
import pe.dcs.app.features.organization.response.OrganizationResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.List;
import java.util.UUID;

public interface OrganizationService {

    PageResponse<OrganizationResponse> findAll(OrganizationListRequest request);

    OrganizationResponse findById(UUID id);

    OrganizationResponse create(OrganizationCreateRequest request);

    OrganizationResponse update(UUID id, OrganizationUpdateRequest request);

    List<OrganizationResponse> list();

    void delete(UUID id);

    void enable(UUID id);

    void disable(UUID id);
}