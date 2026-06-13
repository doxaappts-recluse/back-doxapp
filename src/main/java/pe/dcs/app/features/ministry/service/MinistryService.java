package pe.dcs.app.features.ministry.service;

import pe.dcs.app.features.ministry.request.MinistryRequest;
import pe.dcs.app.features.ministry.request.MinistrySearchRequest;
import pe.dcs.app.features.ministry.response.MinistryResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.List;
import java.util.UUID;

public interface MinistryService {

    MinistryResponse create(MinistryRequest request);

    MinistryResponse update(UUID id, MinistryRequest request);

    void delete(UUID id);

    PageResponse<MinistryResponse> search(MinistrySearchRequest request);

    List<MinistryResponse> findAll();

    MinistryResponse getById(UUID id);

}